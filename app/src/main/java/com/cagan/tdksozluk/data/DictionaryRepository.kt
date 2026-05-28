package com.cagan.tdksozluk.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale

class DictionaryRepository private constructor(private val appContext: Context) {

    @Volatile
    private var entries: List<WordIndexEntry> = emptyList()

    @Volatile
    private var idIndex: Map<String, WordIndexEntry> = emptyMap()

    @Volatile
    private var ready = false

    private val tag = "DictRepo"

    fun isReady(): Boolean = ready

    fun count(): Int = entries.size

    fun ensureIndex(progress: (current: Long, total: Long) -> Unit = { _, _ -> }) {
        if (ready) return
        synchronized(this) {
            if (ready) return
            val indexFile = File(appContext.filesDir, INDEX_FILE)
            val assetLen = appContext.assets.openFd(ASSET_NAME).use { it.length }
            if (indexFile.exists() && indexFile.length() > 16) {
                try {
                    val list = loadIndex(indexFile, assetLen)
                    if (list != null) {
                        entries = list
                        idIndex = buildIdMap(list)
                        ready = true
                        Log.i(tag, "Loaded persisted index: ${list.size} entries")
                        return
                    }
                } catch (e: Exception) {
                    Log.w(tag, "Failed to load index, rebuilding", e)
                    indexFile.delete()
                }
            }
            val list = buildIndex(progress, assetLen)
            try {
                saveIndex(indexFile, list, assetLen)
            } catch (e: Exception) {
                Log.w(tag, "Could not persist index", e)
            }
            entries = list
            idIndex = buildIdMap(list)
            ready = true
        }
    }

    private fun buildIdMap(list: List<WordIndexEntry>): Map<String, WordIndexEntry> {
        val map = HashMap<String, WordIndexEntry>(list.size * 2)
        for (e in list) {
            if (e.maddeId.isNotEmpty()) map[e.maddeId] = e
        }
        return map
    }

    private fun buildIndex(
        progress: (Long, Long) -> Unit,
        assetLen: Long
    ): List<WordIndexEntry> {
        val list = ArrayList<WordIndexEntry>(120_000)
        appContext.assets.open(ASSET_NAME).use { rawIn ->
            BufferedInputStream(rawIn, 1 shl 16).use { input ->
                val lineBuf = ByteArrayOutputStream(2048)
                var offset = 0L
                var lineStart = 0L
                var lastReport = 0L
                while (true) {
                    val b = input.read()
                    if (b == -1) {
                        if (lineBuf.size() > 0) {
                            val line = lineBuf.toString("UTF-8")
                            parseLineHead(line, lineStart, (offset - lineStart).toInt())
                                ?.let(list::add)
                        }
                        break
                    }
                    offset++
                    if (b == '\n'.code) {
                        val length = (offset - lineStart).toInt()
                        val line = lineBuf.toString("UTF-8")
                        parseLineHead(line, lineStart, length)?.let(list::add)
                        lineBuf.reset()
                        lineStart = offset
                        if (offset - lastReport > 2_000_000L) {
                            progress(offset, assetLen)
                            lastReport = offset
                        }
                    } else if (b != '\r'.code) {
                        lineBuf.write(b)
                    }
                }
                progress(offset, assetLen)
            }
        }
        list.sortWith(compareBy(CollatorCmp) { it.maddeDuz })
        return list
    }

    private fun parseLineHead(line: String, offset: Long, length: Int): WordIndexEntry? {
        if (line.isBlank()) return null
        val maddeId = extractField(line, "\"madde_id\":\"") ?: return null
        val rawMadde = extractField(line, "\"madde\":\"") ?: return null
        val madde = unescape(rawMadde)
        if (madde.isBlank()) return null
        val maddeDuzRaw = extractField(line, "\"madde_duz\":\"")
        val maddeDuz = if (maddeDuzRaw != null) unescape(maddeDuzRaw) else madde
        return WordIndexEntry(
            maddeId = maddeId,
            madde = madde,
            maddeDuz = maddeDuz.lowercase(turkishLocale),
            offset = offset,
            length = length
        )
    }

    private fun extractField(line: String, key: String): String? {
        val start = line.indexOf(key)
        if (start < 0) return null
        val from = start + key.length
        val sb = StringBuilder()
        var i = from
        while (i < line.length) {
            val c = line[i]
            if (c == '\\' && i + 1 < line.length) {
                sb.append(c).append(line[i + 1])
                i += 2
                continue
            }
            if (c == '"') return sb.toString()
            sb.append(c)
            i++
        }
        return null
    }

    private fun unescape(s: String): String {
        if (s.indexOf('\\') < 0) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) {
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    '/' -> sb.append('/')
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    'r' -> sb.append('\r')
                    'u' -> if (i + 5 < s.length) {
                        val code = s.substring(i + 2, i + 6).toInt(16)
                        sb.append(code.toChar())
                        i += 6
                        continue
                    }
                    else -> sb.append(n)
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    private fun saveIndex(file: File, list: List<WordIndexEntry>, assetLen: Long) {
        DataOutputStream(FileOutputStream(file).buffered(1 shl 16)).use { out ->
            out.writeInt(INDEX_MAGIC)
            out.writeInt(INDEX_VERSION)
            out.writeLong(assetLen)
            out.writeInt(list.size)
            for (e in list) {
                out.writeUTF(e.maddeId)
                out.writeUTF(e.madde)
                out.writeUTF(e.maddeDuz)
                out.writeLong(e.offset)
                out.writeInt(e.length)
            }
        }
    }

    private fun loadIndex(file: File, assetLen: Long): List<WordIndexEntry>? {
        DataInputStream(FileInputStream(file).buffered(1 shl 16)).use { input ->
            val magic = input.readInt()
            val version = input.readInt()
            val storedLen = input.readLong()
            if (magic != INDEX_MAGIC || version != INDEX_VERSION || storedLen != assetLen) return null
            val n = input.readInt()
            val list = ArrayList<WordIndexEntry>(n)
            repeat(n) {
                val maddeId = input.readUTF()
                val madde = input.readUTF()
                val duz = input.readUTF()
                val off = input.readLong()
                val len = input.readInt()
                list.add(WordIndexEntry(maddeId, madde, duz, off, len))
            }
            return list
        }
    }

    fun search(query: String, limit: Int = 200): List<WordIndexEntry> {
        if (!ready) return emptyList()
        val q = query.trim().lowercase(turkishLocale).normalizeTr()
        if (q.isEmpty()) return entries.take(limit)
        val starts = ArrayList<WordIndexEntry>(limit)
        val contains = ArrayList<WordIndexEntry>(limit)
        for (e in entries) {
            val norm = e.maddeDuz.normalizeTr()
            if (norm.startsWith(q)) {
                if (starts.size < limit) starts.add(e)
                if (starts.size >= limit) break
            } else if (norm.contains(q)) {
                if (contains.size < limit) contains.add(e)
            }
        }
        val merged = ArrayList<WordIndexEntry>(starts.size + contains.size)
        merged.addAll(starts)
        for (e in contains) {
            if (merged.size >= limit) break
            merged.add(e)
        }
        return merged
    }

    fun randomEntry(): WordIndexEntry? {
        if (entries.isEmpty()) return null
        return entries[(Math.random() * entries.size).toInt()]
    }

    fun findByMadde(name: String): WordIndexEntry? {
        val target = name.lowercase(turkishLocale)
        return entries.firstOrNull { it.maddeDuz == target || it.madde.equals(name, ignoreCase = true) }
    }

    fun findById(maddeId: String): WordIndexEntry? = idIndex[maddeId]

    private fun readLineAt(offset: Long, length: Int): String {
        appContext.assets.openFd(ASSET_NAME).use { fd ->
            val baseOffset = fd.startOffset
            FileInputStream(fd.fileDescriptor).use { fis ->
                val buf = ByteArray(length)
                fis.channel.position(baseOffset + offset)
                var read = 0
                while (read < length) {
                    val r = fis.read(buf, read, length - read)
                    if (r <= 0) break
                    read += r
                }
                return String(buf, 0, read, Charsets.UTF_8)
            }
        }
    }

    fun loadMadde(entry: WordIndexEntry): Madde {
        val line = readLineAt(entry.offset, entry.length).trim().trimEnd(',')
        val obj = JSONObject(line)
        return parseMadde(obj)
    }

    fun loadMaddeById(maddeId: String): Madde? {
        val entry = idIndex[maddeId] ?: return null
        return loadMadde(entry)
    }

    private fun parseMadde(obj: JSONObject): Madde {
        val anlamlar = ArrayList<Anlam>()
        val anlamlarListe = obj.optJSONArray("anlamlarListe") ?: JSONArray()
        for (i in 0 until anlamlarListe.length()) {
            val a = anlamlarListe.getJSONObject(i)
            val ozellikler = ArrayList<String>()
            a.optJSONArray("ozelliklerListe")?.let { oz ->
                for (j in 0 until oz.length()) {
                    val name = oz.getJSONObject(j).optString("tam_adi")
                    if (name.isNotEmpty()) ozellikler.add(name)
                }
            }
            val ornekler = ArrayList<Ornek>()
            a.optJSONArray("orneklerListe")?.let { os ->
                for (j in 0 until os.length()) {
                    val o = os.getJSONObject(j)
                    val orn = o.optString("ornek")
                    val yazarArr = o.optJSONArray("yazar")
                    val yazar = if (yazarArr != null && yazarArr.length() > 0)
                        yazarArr.getJSONObject(0).optString("tam_adi") else null
                    if (orn.isNotEmpty()) ornekler.add(Ornek(orn, yazar?.takeIf { it.isNotBlank() }))
                }
            }
            anlamlar.add(
                Anlam(
                    sira = a.optString("anlam_sira"),
                    anlam = a.optString("anlam"),
                    ozellikler = ozellikler,
                    ornekler = ornekler
                )
            )
        }
        val atasozu = ArrayList<AtasozuRef>()
        obj.optJSONArray("atasozu")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                atasozu.add(AtasozuRef(o.optString("madde_id"), o.optString("madde")))
            }
        }
        return Madde(
            maddeId = obj.optString("madde_id"),
            madde = obj.optString("madde"),
            telaffuz = obj.optString("telaffuz").takeIf { it.isNotBlank() && it != "null" },
            lisan = obj.optString("lisan").takeIf { it.isNotBlank() && it != "null" },
            birlesikler = obj.optString("birlesikler").takeIf { it.isNotBlank() && it != "null" },
            anlamlar = anlamlar,
            atasozu = atasozu
        )
    }

    companion object {
        private const val ASSET_NAME = "gts.json"
        private const val INDEX_FILE = "tdk_index_v3.bin"
        private const val INDEX_MAGIC = 0x54444B49 // "TDKI"
        private const val INDEX_VERSION = 3

        private val turkishLocale: Locale = Locale("tr", "TR")
        private val CollatorCmp: Comparator<String> =
            java.text.Collator.getInstance(turkishLocale).let { col ->
                col.strength = java.text.Collator.PRIMARY
                Comparator { a, b -> col.compare(a, b) }
            }

        @Volatile
        private var instance: DictionaryRepository? = null

        fun get(context: Context): DictionaryRepository {
            return instance ?: synchronized(this) {
                instance ?: DictionaryRepository(context.applicationContext).also { instance = it }
            }
        }

        fun String.normalizeTr(): String {
            if (isEmpty()) return this
            val sb = StringBuilder(length)
            for (ch in this) {
                sb.append(
                    when (ch) {
                        'ç' -> 'c'; 'Ç' -> 'c'
                        'ğ' -> 'g'; 'Ğ' -> 'g'
                        'ı' -> 'i'; 'İ' -> 'i'
                        'ö' -> 'o'; 'Ö' -> 'o'
                        'ş' -> 's'; 'Ş' -> 's'
                        'ü' -> 'u'; 'Ü' -> 'u'
                        'â' -> 'a'; 'î' -> 'i'; 'û' -> 'u'
                        else -> ch
                    }
                )
            }
            return sb.toString()
        }
    }
}
