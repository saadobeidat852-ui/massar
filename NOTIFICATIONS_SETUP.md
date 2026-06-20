# الإشعارات الحقيقية — شو اشتغل الآن وشو محتاج خطوة إضافية

## ✅ شغّال حالًا (بعد بناء APK جديد)
أي إشعار تطلقه صفحة الويب (تذكير حصة قبل 5 دقائق، أو تنبيه تغيّر مسار طالب) بيظهر
كإشعار حقيقي بشريط إشعارات الموبايل (مو بس رسالة داخل الشاشة) — **طالما التطبيق
شغّال بالخلفية أو بالواجهة** (يعني المستخدم فاتحه ولسا ما قفله/قتله من الذاكرة).

هاد يغطي تقريبًا كل حالات الاستخدام العادية (المعلم فاتح التطبيق بالصباح وشغّال بالخلفية
طول اليوم الدراسي).

## ⏳ يحتاج خطوة إضافية: إشعار يوصل حتى لو التطبيق مغلق تمامًا
هاد يتطلب **Firebase Cloud Messaging (FCM)** + **Cloud Functions** (كود يشتغل على
خوادم Firebase نفسها، مش داخل التطبيق). الخطوات:

### 1) تفعيل خطة Blaze على Firebase
Cloud Functions تتطلب خطة الدفع حسب الاستخدام (فيها مستوى مجاني سخي، ما رح تدفع
شي إلا لو تجاوزت آلاف الاستدعاءات شهريًا). من Firebase Console → Settings → Usage and billing.

### 2) ربط تطبيق Android بمشروع Firebase
- روح Firebase Console → ⚙️ Project Settings → Your apps → Add app → Android
- Package name: `com.masaar.app`
- نزّل ملف `google-services.json` وحطه داخل مجلد `app/` (جنب `build.gradle` تبع app)
- قلّي بعدين وبضيف كود تسجيل الـ FCM token تلقائيًا بـ MainActivity.java

### 3) نشر الـ Cloud Functions
داخل مجلد `functions/` (موجود أصلاً بالمشروع):
```bash
npm install -g firebase-tools
firebase login
firebase init functions   # اختر نفس مشروع massar-edu
firebase deploy --only functions
```

### 4) تخزين FCM Token لكل معلم
بعد ربط `google-services.json`، التطبيق لازم يسجّل الـ token بمجموعة `fcmTokens`
بـ Firestore (وثيقة لكل معلم). هاد الجزء جاهز بالكود (`functions/index.js`) بانتظار
ربط الخطوة 2 و3.

---

**خلاصة:** الإشعارات الأساسية شغّالة فعليًا الآن. الإشعار اللي يوصل والتطبيق مقفول
بالكامل يحتاج مني أكمل ربط الخطوات فوق بعد ما تجهز حساب Firebase Blaze — خبرني
لما توصل لهاي المرحلة وكمل معك خطوة بخطوة.
