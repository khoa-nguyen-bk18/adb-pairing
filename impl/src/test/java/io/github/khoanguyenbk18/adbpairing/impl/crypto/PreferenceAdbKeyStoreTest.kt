package io.github.khoanguyenbk18.adbpairing.impl.crypto

import android.content.SharedPreferences
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.HashMap

class PreferenceAdbKeyStoreTest {

    private class FakeSharedPreferences : SharedPreferences {
        val map = HashMap<String, Any?>()

        override fun contains(key: String): Boolean = map.containsKey(key)
        override fun getString(key: String, defValue: String?): String? = (map[key] as? String) ?: defValue
        override fun edit(): SharedPreferences.Editor = Editor()

        inner class Editor : SharedPreferences.Editor {
            override fun putString(key: String, value: String?): SharedPreferences.Editor {
                map[key] = value
                return this
            }
            override fun commit(): Boolean = true
            override fun apply() {}
            override fun remove(key: String): SharedPreferences.Editor = this
            override fun clear(): SharedPreferences.Editor = this
            override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor = this
            override fun putInt(key: String, value: Int): SharedPreferences.Editor = this
            override fun putLong(key: String, value: Long): SharedPreferences.Editor = this
            override fun putFloat(key: String, value: Float): SharedPreferences.Editor = this
            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = this
        }

        override fun getAll(): MutableMap<String, *> = map
        override fun getInt(key: String, defValue: Int): Int = defValue
        override fun getLong(key: String, defValue: Long): Long = defValue
        override fun getFloat(key: String, defValue: Float): Float = defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = defValue
        override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    @Test
    fun testDifferentAliases_storedIndependently() {
        val prefs = FakeSharedPreferences()
        val store = PreferenceAdbKeyStore(prefs)

        val keyA = byteArrayOf(1, 2, 3)
        val keyB = byteArrayOf(4, 5, 6)

        store.save("aliasA", keyA)
        store.save("aliasB", keyB)

        assertArrayEquals(keyA, store.load("aliasA"))
        assertArrayEquals(keyB, store.load("aliasB"))
        assertNull(store.load("aliasC"))
    }
}
