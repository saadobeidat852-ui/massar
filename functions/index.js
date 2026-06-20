/**
 * هيكل أولي (Scaffold) لـ Firebase Cloud Functions
 * الهدف: إرسال إشعارات حقيقية (Push) تصل حتى لو التطبيق مغلق بالكامل.
 *
 * ⚠️ هذا الملف غير مفعّل تلقائيًا — يحتاج خطوات إضافية موضّحة في NOTIFICATIONS_SETUP.md
 * قبل ما يشتغل:
 *   1) تفعيل خطة Firebase "Blaze" (الدفع حسب الاستخدام) — مطلوبة لتشغيل Cloud Functions
 *   2) ربط تطبيق Android بمشروع Firebase (google-services.json)
 *   3) تخزين FCM token لكل معلم بمجموعة (Collection) اسمها fcmTokens
 *   4) تشغيل: firebase deploy --only functions
 */

const functions = require('firebase-functions');
const admin = require('firebase-admin');
admin.initializeApp();

const db = admin.firestore();
const messaging = admin.messaging();

/**
 * تشغيل كل دقيقة: يفحص جداول الحصص (collection: schedules) للمعلمين،
 * ويرسل إشعار Push قبل 5 دقائق من بداية كل حصة.
 */
exports.checkUpcomingPeriods = functions.pubsub.schedule('every 1 minutes').onRun(async () => {
  const now = new Date();
  const days = ['الأحد', 'الاثنين', 'الثلاثاء', 'الأربعاء', 'الخميس'];
  const todayName = days[now.getDay()];
  const nowMinutes = now.getHours() * 60 + now.getMinutes();

  const schedulesSnap = await db.collection('schedules').get();

  const sends = [];
  schedulesSnap.forEach(docSnap => {
    const teacherId = docSnap.id;
    const periods = (docSnap.data().periods || []).filter(p => p.day === todayName);

    periods.forEach(p => {
      const [h, m] = (p.start || '0:0').split(':').map(Number);
      const startMinutes = h * 60 + m;
      const diff = startMinutes - nowMinutes;
      if (diff === 5) { // قبل 5 دقائق بالضبط (يعمل كل دقيقة فيلتقطها مرة وحدة)
        sends.push(sendToTeacher(teacherId,
          'تذكير حصة',
          `حصة ${p.subject} (${p.class || ''} ${p.section || ''}) تبدأ خلال 5 دقائق`));
      }
    });
  });

  await Promise.all(sends);
  return null;
});

/**
 * يعمل تلقائيًا عند أي تحديث على وثيقة الطالب (Firestore Trigger)
 * يقارن المسار الجديد بالمسار المحفوظ سابقًا، ويرسل إشعار لو تغيّر.
 * (يفترض أن الواجهة تكتب أحدث تصنيف لكل محور ضمن الحقل trackSnapshot عند كل حفظ)
 */
exports.onStudentTrackChange = functions.firestore
  .document('students/{studentId}')
  .onUpdate(async (change, context) => {
    const before = change.before.data().trackSnapshot || {};
    const after = change.after.data().trackSnapshot || {};
    const teacherId = change.after.data().teacherId; // تأكد من وجود هذا الحقل بوثيقة الطالب
    if (!teacherId) return null;

    const axes = ['أكاديمي', 'سلوكي', 'وجداني'];
    const changedAxes = axes.filter(a => before[a] && after[a] && before[a] !== after[a]);
    if (!changedAxes.length) return null;

    const studentName = change.after.data().name || 'طالب';
    const msg = changedAxes.map(a => `${a}: ${after[a]}`).join(' — ');
    return sendToTeacher(teacherId, `تغيّر مسار ${studentName}`, msg);
  });

async function sendToTeacher(teacherId, title, body) {
  const tokenDoc = await db.collection('fcmTokens').doc(teacherId).get();
  if (!tokenDoc.exists) return null;
  const token = tokenDoc.data().token;
  if (!token) return null;

  return messaging.send({
    token,
    notification: { title, body },
    android: { priority: 'high' },
  }).catch(err => console.error('فشل إرسال الإشعار:', err));
}
