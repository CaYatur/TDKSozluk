package com.cagan.tdksozluk.data

data class WordIndexEntry(
    val maddeId: String,
    val madde: String,
    val maddeDuz: String,
    val offset: Long,
    val length: Int
)

data class Madde(
    val maddeId: String,
    val madde: String,
    val telaffuz: String?,
    val lisan: String?,
    val birlesikler: String?,
    val anlamlar: List<Anlam>,
    val atasozu: List<AtasozuRef>
)

data class Anlam(
    val sira: String,
    val anlam: String,
    val ozellikler: List<String>,
    val ornekler: List<Ornek>
)

data class Ornek(
    val ornek: String,
    val yazar: String?
)

data class AtasozuRef(
    val maddeId: String,
    val madde: String
)
