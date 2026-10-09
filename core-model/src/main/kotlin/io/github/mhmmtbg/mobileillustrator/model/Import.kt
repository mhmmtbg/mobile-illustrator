package io.github.mhmmtbg.mobileillustrator.model

/** Bir dosyanın içe aktarılmasının sonucu. */
class ImportResult(val document: Document, /** Kullanıcıya gösterilecek, birebir aktarılamayan özellikler. */ val warnings: List<String>)

/** Dosya açılamadığında kullanıcıya gösterilecek açıklamayı taşır. */
class ImportException(message: String) : Exception(message)
