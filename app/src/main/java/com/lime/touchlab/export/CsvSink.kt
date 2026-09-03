package com.lime.touchlab.export

import java.io.OutputStreamWriter

/**
 * Запись CSV одного файла архива.
 *
 * Экранирование по RFC 4180: поле берётся в кавычки, если содержит запятую, кавычку
 * или перевод строки; кавычка внутри удваивается. `null` печатается пустым полем.
 * Вещественные значения печатаются через `toString`, который не зависит от локали, —
 * запятая вместо точки сломала бы разбор.
 *
 * Шапка печатается из [FileSpec], и каждая строка данных сверяется с ней по длине.
 */
internal class CsvSink(
    private val writer: OutputStreamWriter,
    private val spec: FileSpec,
) {

    fun header() {
        write(spec.columns.map { it.name })
    }

    fun row(vararg cells: Any?) {
        if (cells.size != spec.columns.size) {
            throw IllegalStateException(
                spec.fileName + ": объявлено " + spec.columns.size +
                    " колонок, в строке " + cells.size,
            )
        }
        write(cells.asList())
    }

    private fun write(cells: List<Any?>) {
        val sb = StringBuilder()
        for (i in cells.indices) {
            if (i > 0) sb.append(',')
            sb.append(escape(cells[i]))
        }
        sb.append('\n')
        writer.write(sb.toString())
    }

    private fun escape(value: Any?): String {
        if (value == null) return ""
        val text = value.toString()
        val needsQuotes = text.indexOf(',') >= 0 ||
            text.indexOf('"') >= 0 ||
            text.indexOf('\n') >= 0 ||
            text.indexOf('\r') >= 0
        if (!needsQuotes) return text
        return "\"" + text.replace("\"", "\"\"") + "\""
    }
}
