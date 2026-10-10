package io.github.mhmmtbg.mobileillustrator.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Uygulamanın kendi 24dp simgeleri; büyük simge kitaplığına bağımlılık yok. */
object AppIcons {
    private fun icon(name: String, pathData: String, evenOdd: Boolean = false): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .addPath(
                pathData = addPathNodes(pathData),
                fill = SolidColor(Color.Black),
                pathFillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
            )
            .build()

    // Araçlar
    val Select by lazy { icon("Select", "M5,3 L5,19 L9.5,14.8 L12.4,21 L14.8,19.9 L11.9,13.8 L18,13.5 Z") }
    val Direct by lazy {
        icon("Direct", "M5,3 L5,19 L9.5,14.8 L12.4,21 L14.8,19.9 L11.9,13.8 L18,13.5 Z M6.8,7.2 L13.6,12.1 L9.4,12.3 L6.8,14.8 Z", evenOdd = true)
    }
    val Pen by lazy {
        icon(
            "Pen",
            "M12,2 L17.5,12.5 L15,17 L9,17 L6.5,12.5 Z M12,6.2 L8.8,12.4 L10.2,15 L11.2,15 L11.2,11.6 " +
                "A1.6,1.6 0 1 1 12.8,11.6 L12.8,15 L13.8,15 L15.2,12.4 Z M8.5,18.5 H15.5 V22 H8.5 Z",
            evenOdd = true,
        )
    }
    val Pencil by lazy {
        icon(
            "Pencil",
            "M3,17.25V21h3.75L17.81,9.94l-3.75,-3.75L3,17.25zM20.71,7.04c0.39,-0.39 0.39,-1.02 0,-1.41l-2.34,-2.34" +
                "c-0.39,-0.39 -1.02,-0.39 -1.41,0l-1.83,1.83 3.75,3.75 1.83,-1.83z",
        )
    }
    val Rectangle by lazy { icon("Rectangle", "M3,5 H21 V19 H3 Z M5,7 V17 H19 V7 Z", evenOdd = true) }
    val Ellipse by lazy {
        icon("Ellipse", "M12,4 A8,8 0 1 0 12,20 A8,8 0 1 0 12,4 Z M12,6 A6,6 0 1 1 12,18 A6,6 0 1 1 12,6 Z", evenOdd = true)
    }
    val Line by lazy { icon("Line", "M4.7,20.7 L3.3,19.3 L19.3,3.3 L20.7,4.7 Z") }
    val Text by lazy { icon("Text", "M5,4v3h5.5v12h3V7H19V4z") }
    val Eyedropper by lazy {
        icon(
            "Eyedropper",
            "M20.71,5.63l-2.34,-2.34c-0.39,-0.39 -1.02,-0.39 -1.41,0l-3.12,3.12 -1.93,-1.91 -1.41,1.41 1.42,1.42L3,16.25V21h4.75" +
                "l8.92,-8.92 1.42,1.42 1.41,-1.41 -1.92,-1.92 3.12,-3.12c0.4,-0.4 0.4,-1.03 0.01,-1.42zM6.92,19L5,17.08l8.06,-8.06 1.92,1.92L6.92,19z",
        )
    }
    val Hand by lazy {
        icon(
            "Hand",
            "M23,5.5V20c0,2.2 -1.8,4 -4,4h-7.3c-1.08,0 -2.1,-0.43 -2.85,-1.19L1,14.83s1.26,-1.23 1.3,-1.25c0.22,-0.19 0.49,-0.29 0.79,-0.29 " +
                "0.22,0 0.42,0.06 0.6,0.16 0.04,0.01 4.31,2.46 4.31,2.46V4c0,-0.83 0.67,-1.5 1.5,-1.5S11,3.17 11,4v7h1V1.5c0,-0.83 0.67,-1.5 1.5,-1.5" +
                "S15,0.67 15,1.5V11h1V2.5c0,-0.83 0.67,-1.5 1.5,-1.5s1.5,0.67 1.5,1.5V11h1V5.5c0,-0.83 0.67,-1.5 1.5,-1.5s1.5,0.67 1.5,1.5z",
        )
    }

    // Eylemler
    val Undo by lazy {
        icon(
            "Undo",
            "M12.5,8c-2.65,0 -5.05,0.99 -6.9,2.6L2,7v9h9l-3.62,-3.62c1.39,-1.16 3.16,-1.88 5.12,-1.88 " +
                "3.54,0 6.55,2.31 7.6,5.5l2.37,-0.78C21.08,11.03 17.15,8 12.5,8z",
        )
    }
    val Redo by lazy {
        icon(
            "Redo",
            "M18.4,10.6C16.55,8.99 14.15,8 11.5,8c-4.65,0 -8.58,3.03 -9.96,7.22L3.9,16c1.05,-3.19 4.05,-5.5 " +
                "7.6,-5.5 1.95,0 3.73,0.72 5.12,1.88L13,16h9V7l-3.6,3.6z",
        )
    }
    val Delete by lazy {
        icon("Delete", "M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7H6v12zM19,4h-3.5l-1,-1h-5l-1,1H5v2h14V4z")
    }
    val Fit by lazy {
        icon(
            "Fit",
            "M3,5v4h2V5h4V3H5C3.9,3 3,3.9 3,5zM5,15H3v4c0,1.1 0.9,2 2,2h4v-2H5V15zM19,19h-4v2h4c1.1,0 2,-0.9 " +
                "2,-2v-4h-2V19zM19,3h-4v2h4v4h2V5C21,3.9 20.1,3 19,3z",
        )
    }
    val Layers by lazy {
        icon("Layers", "M11.99,18.54l-7.37,-5.73L3,14.07l9,7 9,-7 -1.63,-1.27 -7.38,5.74zM12,16l7.36,-5.73L21,9l-9,-7 -9,7 1.63,1.27L12,16z")
    }
    val Menu by lazy { icon("Menu", "M3,18h18v-2H3v2zM3,13h18v-2H3v2zM3,6v2h18V6H3z") }
    val More by lazy {
        icon(
            "More",
            "M12,8c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2zM12,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 " +
                "-0.9,-2 -2,-2zM12,16c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2z",
        )
    }
    val Add by lazy { icon("Add", "M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z") }
    val Close by lazy { icon("Close", "M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z") }
    val ChevronRight by lazy { icon("ChevronRight", "M10,6L8.59,7.41 13.17,12l-4.58,4.59L10,18l6,-6z") }
    val ExpandMore by lazy { icon("ExpandMore", "M16.59,8.59L12,13.17 7.41,8.59 6,10l6,6 6,-6z") }
    val Visible by lazy {
        icon(
            "Visible",
            "M12,4.5C7,4.5 2.73,7.61 1,12c1.73,4.39 6,7.5 11,7.5s9.27,-3.11 11,-7.5c-1.73,-4.39 -6,-7.5 -11,-7.5zM12,17" +
                "c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5zM12,9c-1.66,0 -3,1.34 -3,3s1.34,3 3,3 3,-1.34 3,-3 -1.34,-3 -3,-3z",
        )
    }
    val Hidden by lazy {
        icon(
            "Hidden",
            "M12,7c2.76,0 5,2.24 5,5 0,0.65 -0.13,1.26 -0.36,1.83l2.92,2.92c1.51,-1.26 2.7,-2.89 3.43,-4.75 -1.73,-4.39 " +
                "-6,-7.5 -11,-7.5 -1.4,0 -2.74,0.25 -3.98,0.7l2.16,2.16C10.74,7.13 11.35,7 12,7zM2,4.27l2.28,2.28 0.46,0.46" +
                "C3.08,8.3 1.78,10.02 1,12c1.73,4.39 6,7.5 11,7.5 1.55,0 3.03,-0.3 4.38,-0.84l0.42,0.42L19.73,22 21,20.73 3.27,3 " +
                "2,4.27zM7.53,9.8l1.55,1.55c-0.05,0.21 -0.08,0.43 -0.08,0.65 0,1.66 1.34,3 3,3 0.22,0 0.44,-0.03 0.65,-0.08l1.55,1.55" +
                "c-0.67,0.33 -1.41,0.53 -2.2,0.53 -2.76,0 -5,-2.24 -5,-5 0,-0.79 0.2,-1.53 0.53,-2.2zM11.84,9.02l3.15,3.15 0.02,-0.16" +
                "c0,-1.66 -1.34,-3 -3,-3l-0.17,0.01z",
        )
    }
    val Lock by lazy {
        icon(
            "Lock",
            "M18,8h-1V6c0,-2.76 -2.24,-5 -5,-5S7,3.24 7,6v2H6c-1.1,0 -2,0.9 -2,2v10c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V10" +
                "c0,-1.1 -0.9,-2 -2,-2zM12,17c-1.1,0 -2,-0.9 -2,-2s0.9,-2 2,-2 2,0.9 2,2 -0.9,2 -2,2zM15.1,8H8.9V6c0,-1.71 1.39,-3.1 " +
                "3.1,-3.1 1.71,0 3.1,1.39 3.1,3.1v2z",
        )
    }
    val Unlock by lazy {
        icon(
            "Unlock",
            "M12,17c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2zM18,8h-1V6c0,-2.76 -2.24,-5 -5,-5S7,3.24 7,6h1.9" +
                "c0,-1.71 1.39,-3.1 3.1,-3.1 1.71,0 3.1,1.39 3.1,3.1v2H6c-1.1,0 -2,0.9 -2,2v10c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V10" +
                "c0,-1.1 -0.9,-2 -2,-2zM18,20H6V10h12v10z",
        )
    }
    val Up by lazy { icon("Up", "M4,12l1.41,1.41L11,7.83V20h2V7.83l5.58,5.59L20,12l-8,-8 -8,8z") }
    val Down by lazy { icon("Down", "M20,12l-1.41,-1.41L13,16.17V4h-2v12.17l-5.58,-5.59L4,12l8,8 8,-8z") }

    // ---- Seçim çubuğu ve pano simgeleri ----------------------------------------
    // Basit geometrik simgeler dikdörtgenlerden kurulur: r = dolu dikdörtgen, frame = çerçeve (dört şerit).

    private fun r(x: Number, y: Number, w: Number, h: Number) = "M$x,$y h$w v$h h-$w z "
    private fun frame(x: Double, y: Double, w: Double, h: Double, t: Double = 1.5) =
        r(x, y, w, t) + r(x, y + h - t, w, t) + r(x, y + t, t, h - 2 * t) + r(x + w - t, y + t, t, h - 2 * t)
    private fun circle(cx: Number, cy: Double, rad: Number) = "M$cx,${cy - rad.toDouble()} a$rad,$rad 0 1 0 0.01,0 z "

    val Copy by lazy {
        icon("Copy", "M16,1H4C2.9,1 2,1.9 2,3v14h2V3h12V1zM19,5H8C6.9,5 6,5.9 6,7v14c0,1.1 0.9,2 2,2h11c1.1,0 2,-0.9 2,-2V7C21,5.9 20.1,5 19,5zM19,21H8V7h11V21z")
    }
    val Paste by lazy {
        icon(
            "Paste",
            "M19,2h-4.18C14.4,0.84 13.3,0 12,0c-1.3,0 -2.4,0.84 -2.82,2H5C3.9,2 3,2.9 3,4v16c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2V4" +
                "C21,2.9 20.1,2 19,2zM12,2c0.55,0 1,0.45 1,1s-0.45,1 -1,1 -1,-0.45 -1,-1 0.45,-1 1,-1zM19,20H5V4h2v3h10V4h2V20z",
        )
    }
    val Cut by lazy {
        icon(
            "Cut",
            "M9.64,7.64c0.23,-0.5 0.36,-1.05 0.36,-1.64 0,-2.21 -1.79,-4 -4,-4S2,3.79 2,6s1.79,4 4,4c0.59,0 1.14,-0.13 1.64,-0.36L10,12" +
                "l-2.36,2.36C7.14,14.13 6.59,14 6,14c-2.21,0 -4,1.79 -4,4s1.79,4 4,4 4,-1.79 4,-4c0,-0.59 -0.13,-1.14 -0.36,-1.64L12,14l7,7h3v-1" +
                "L9.64,7.64zM6,8c-1.1,0 -2,-0.89 -2,-2s0.9,-2 2,-2 2,0.89 2,2 -0.9,2 -2,2zM6,20c-1.1,0 -2,-0.89 -2,-2s0.9,-2 2,-2 2,0.89 2,2 " +
                "-0.9,2 -2,2zM12,12.5c-0.28,0 -0.5,-0.22 -0.5,-0.5s0.22,-0.5 0.5,-0.5 0.5,0.22 0.5,0.5 -0.22,0.5 -0.5,0.5zM19,3l-6,6 2,2 7,-7V3z",
        )
    }
    val Checked by lazy {
        icon("Checked", "M19,3H5C3.89,3 3,3.9 3,5v14c0,1.1 0.89,2 2,2h14c1.11,0 2,-0.9 2,-2V5C21,3.9 20.11,3 19,3zM10,17l-5,-5 1.41,-1.41L10,14.17l7.59,-7.59L19,8l-9,9z")
    }
    val Unchecked by lazy {
        icon("Unchecked", "M19,5v14H5V5h14m0,-2H5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2V5c0,-1.1 -0.9,-2 -2,-2z")
    }
    val MultiSelect by lazy {
        icon("MultiSelect", r(3, 4, 4, 4) + r(9, 5, 12, 2) + r(3, 10, 4, 4) + r(9, 11, 12, 2) + frame(3.0, 16.0, 4.0, 4.0, 1.0) + r(9, 17, 12, 2))
    }
    val Check by lazy { icon("Check", "M9,16.17L4.83,12l-1.42,1.41L9,19 21,7l-1.41,-1.41z") }
    val Back by lazy { icon("Back", "M15.41,7.41L14,6l-6,6 6,6 1.41,-1.41L10.83,12z") }
    val Import by lazy { icon("Import", "M12,3 h1 v8.2 l3.1,-3.1 1.4,1.4 -5.5,5.5 -5.5,-5.5 1.4,-1.4 3.1,3.1 V3 z " + r(4, 17, 16, 2) + r(4, 13, 2, 4) + r(18, 13, 2, 4)) }

    val AlignLeft by lazy { icon("AlignLeft", r(3, 3, 2, 18) + r(7, 6, 13, 4) + r(7, 14, 8, 4)) }
    val AlignCenterH by lazy { icon("AlignCenterH", r(11, 3, 2, 18) + r(4, 6, 16, 4) + r(7, 14, 10, 4)) }
    val AlignRight by lazy { icon("AlignRight", r(19, 3, 2, 18) + r(4, 6, 13, 4) + r(9, 14, 8, 4)) }
    val AlignTop by lazy { icon("AlignTop", r(3, 3, 18, 2) + r(6, 7, 4, 13) + r(14, 7, 4, 8)) }
    val AlignCenterV by lazy { icon("AlignCenterV", r(3, 11, 18, 2) + r(6, 4, 4, 16) + r(14, 7, 4, 10)) }
    val AlignBottom by lazy { icon("AlignBottom", r(3, 19, 18, 2) + r(6, 4, 4, 13) + r(14, 9, 4, 8)) }
    val DistributeH by lazy { icon("DistributeH", r(3, 3, 2, 18) + r(19, 3, 2, 18) + r(9.5, 7, 5, 10)) }
    val DistributeV by lazy { icon("DistributeV", r(3, 3, 18, 2) + r(3, 19, 18, 2) + r(7, 9.5, 10, 5)) }

    val Shapes by lazy { icon("Shapes", frame(3.0, 3.0, 13.0, 13.0, 2.0) + frame(8.0, 8.0, 13.0, 13.0, 2.0)) }
    val Unite by lazy { icon("Unite", "M3,3 H16 V8 H21 V21 H8 V16 H3 Z") }
    val MinusFront by lazy { icon("MinusFront", "M3,3 H16 V8 H8 V16 H3 Z " + frame(9.5, 9.5, 11.5, 11.5, 1.5)) }
    val Intersect by lazy { icon("Intersect", frame(3.0, 3.0, 13.0, 13.0, 1.5) + frame(8.0, 8.0, 13.0, 13.0, 1.5) + r(9.5, 9.5, 5, 5)) }
    val Exclude by lazy { icon("Exclude", r(3, 3, 13, 13) + r(8, 8, 13, 13), evenOdd = true) }
    val Mask by lazy { icon("Mask", r(3, 3, 18, 18) + circle(12, 12.0, 6), evenOdd = true) }
    val Unmask by lazy { icon("Unmask", frame(3.0, 3.0, 18.0, 18.0, 1.5) + circle(12, 12.0, 6) + circle(12, 12.0, 4.5), evenOdd = true) }
    val Transform by lazy {
        icon("Transform", frame(5.0, 5.0, 14.0, 14.0, 1.5) + r(3, 3, 5, 5) + r(16, 3, 5, 5) + r(3, 16, 5, 5) + r(16, 16, 5, 5))
    }
    val Duplicate by lazy {
        icon("Duplicate", "M3,3 H15 V5 H5 V15 H3 Z " + r(8, 8, 13, 13) + "M13.5,10.5 h2 v3 h3 v2 h-3 v3 h-2 v-3 h-3 v-2 h3 z", evenOdd = true)
    }
    val Group by lazy { icon("Group", frame(3.0, 3.0, 18.0, 18.0, 1.5) + r(6.5, 6.5, 5, 5) + r(12.5, 12.5, 5, 5)) }
    val Ungroup by lazy { icon("Ungroup", frame(3.0, 3.0, 9.0, 9.0, 2.0) + frame(12.0, 12.0, 9.0, 9.0, 2.0)) }
    val Forward by lazy { icon("Forward", "M12,4 l-7,7 h4.5 v9 h5 v-9 H19 z") }
    val Backward by lazy { icon("Backward", "M12,20 l-7,-7 h4.5 v-9 h5 v9 H19 z") }
    val ToFront by lazy { icon("ToFront", r(4, 3, 16, 2) + "M12,7 l-7,7 h4.5 v7 h5 v-7 H19 z") }
    val ToBack by lazy { icon("ToBack", r(4, 19, 16, 2) + "M12,17 l-7,-7 h4.5 v-7 h5 v7 H19 z") }
    val Rename by lazy { icon("Rename", frame(2.0, 8.0, 20.0, 8.0, 1.5) + r(6, 4, 1.5, 16) + r(4.5, 4, 4.5, 1.5) + r(4.5, 18.5, 4.5, 1.5)) }
    val Nodes by lazy { icon("Nodes", r(3, 16, 5, 5) + r(16, 3, 5, 5) + "M7.4,18 L6,16.6 L16.6,6 L18,7.4 Z") }
    val Picture by lazy { icon("Picture", frame(3.0, 4.0, 18.0, 16.0, 1.5) + "M6,17 l4.5,-6 l3,4 l2,-2.5 l3.5,4.5 z " + circle(8, 8.5, 1.5)) }
    val Flat by lazy { icon("Flat", r(3, 11, 18, 2)) }
    val Arc by lazy { icon("Arc", "M3,17 A9,9 0 0 1 21,17 L18.5,17 A6.5,6.5 0 0 0 5.5,17 Z") }
    val Arch by lazy { icon("Arch", "M3,19 V13 A9,9 0 0 1 21,13 V19 H18.5 V13 A6.5,6.5 0 0 0 5.5,13 V19 Z") }
    val Wave by lazy { icon("Wave", "M3,12 C6,4 9,4 12,11 C15,18 18,18 21,10 L21,13.5 C18,21.5 15,21.5 12,14.5 C9,7.5 6,7.5 3,15.5 Z") }
    val Bulge by lazy { icon("Bulge", "M3,12 Q12,2 21,12 Q12,22 3,12 Z M6.5,12 Q12,17.5 17.5,12 Q12,6.5 6.5,12 Z", evenOdd = true) }
    val Rise by lazy { icon("Rise", "M3,16 L21,5 V8.5 L3,19.5 Z") }
    val Corner by lazy { icon("Corner", "M3,19 L12,5 L21,19 L18,19 L12,9.7 L6,19 Z") }
    val Loop by lazy { icon("Loop", circle(12, 12.0, 8) + circle(12, 12.0, 5.5), evenOdd = true) }

    val Palette by lazy {
        icon(
            "Palette",
            "M12,3c-4.97,0 -9,4.03 -9,9s4.03,9 9,9c0.83,0 1.5,-0.67 1.5,-1.5 0,-0.39 -0.15,-0.74 -0.39,-1.01 -0.23,-0.26 -0.38,-0.61 -0.38,-0.99 " +
                "0,-0.83 0.67,-1.5 1.5,-1.5H16c2.76,0 5,-2.24 5,-5 0,-4.42 -4.03,-8 -9,-8zM6.5,12c-0.83,0 -1.5,-0.67 -1.5,-1.5S5.67,9 6.5,9 8,9.67 8,10.5 " +
                "7.33,12 6.5,12zM9.5,8C8.67,8 8,7.33 8,6.5S8.67,5 9.5,5s1.5,0.67 1.5,1.5S10.33,8 9.5,8zM14.5,8c-0.83,0 -1.5,-0.67 -1.5,-1.5S13.67,5 14.5,5" +
                "s1.5,0.67 1.5,1.5S15.33,8 14.5,8zM17.5,12c-0.83,0 -1.5,-0.67 -1.5,-1.5S16.67,9 17.5,9s1.5,0.67 1.5,1.5 -0.67,1.5 -1.5,1.5z",
        )
    }
    val TextLeft by lazy { icon("TextLeft", r(3, 5, 18, 2) + r(3, 9, 12, 2) + r(3, 13, 18, 2) + r(3, 17, 10, 2)) }
    val TextCenter by lazy { icon("TextCenter", r(3, 5, 18, 2) + r(6, 9, 12, 2) + r(3, 13, 18, 2) + r(7, 17, 10, 2)) }
    val TextRight by lazy { icon("TextRight", r(3, 5, 18, 2) + r(9, 9, 12, 2) + r(3, 13, 18, 2) + r(11, 17, 10, 2)) }
}
