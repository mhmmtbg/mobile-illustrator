package io.github.mhmmtbg.mobileillustrator.model

/** Bir dosyanın içe aktarılmasının sonucu. */
class ImportResult(
    val document: Document,
    /** Kullanıcıya gösterilecek, birebir aktarılamayan özellikler. */
    val warnings: List<String>,
    /**
     * Dosyayı bu uygulama mı yazmış? Öyleyse üzerine kaydetmek kayıpsızdır. Başka bir programın
     * (ör. Illustrator) dosyasının üzerine yazmak, o programa özgü verileri sileceği için sorulmadan yapılmaz.
     */
    val writtenByThisApp: Boolean = false,
)

/** Dosya açılamadığında kullanıcıya gösterilecek açıklamayı taşır. */
class ImportException(message: String) : Exception(message)
