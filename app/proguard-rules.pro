# CustomerRegister R8 rules.
# Room, WorkManager, Navigation and Compose ship their own consumer rules.

# Keep enum names stable: they are stored in the database, DataStore and backup files.
-keepclassmembers enum com.customerregister.app.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    <fields>;
}

# Workers are instantiated reflectively by WorkManager.
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Room entities (column names are derived from field names).
-keep class com.customerregister.app.data.db.** { *; }
