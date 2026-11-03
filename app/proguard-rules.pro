# VahakVigil R8 / ProGuard Configuration for Release Builds

# ── Room Database ─────────────────────────────────────────────────────────────
# Room uses reflection to load the generated database implementation (e.g. AppDatabase_Impl)
-keep class * extends androidx.room.RoomDatabase {
    <init>();
}
-keep class com.manish.vahakvigil.data.local.db.**_Impl {
    <init>();
}

# Preserve Room entities and their fields/constructors for SQLite table mapping
-keep class com.manish.vahakvigil.data.local.db.entity.** {
    <fields>;
    <init>(...);
}

# Preserve Room DAO interfaces and methods for query generation and execution
-keep interface com.manish.vahakvigil.data.local.db.dao.** {
    *;
}

# ── Lifecycle & ViewModels ────────────────────────────────────────────────────
# Preserve ViewModel constructors instantiated through ViewModelProvider factories
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
