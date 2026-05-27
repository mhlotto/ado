package com.ado.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.FileOutputStream

data class ChecklistPrintItem(
    val name: String,
    val indentLevel: Int = 0,
)

fun printChecklist(
    context: Context,
    title: String,
    items: List<ChecklistPrintItem>,
) {
    val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    manager.print(
        title,
        ChecklistPrintAdapter(context, title, items),
        PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.NA_LETTER.asPortrait())
            .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
            .build(),
    )
}

private class ChecklistPrintAdapter(
    private val context: Context,
    private val title: String,
    private val items: List<ChecklistPrintItem>,
) : PrintDocumentAdapter() {
    private var attributes = PrintAttributes.Builder().build()

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal,
        callback: LayoutResultCallback,
        extras: Bundle?,
    ) {
        if (cancellationSignal.isCanceled) {
            callback.onLayoutCancelled()
            return
        }
        attributes = newAttributes
        callback.onLayoutFinished(
            PrintDocumentInfo.Builder("$title.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(1)
                .build(),
            oldAttributes != newAttributes,
        )
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal,
        callback: WriteResultCallback,
    ) {
        val document = PrintedPdfDocument(context, attributes)
        try {
            val page = document.startPage(0)
            val contentLeft = page.info.contentRect.left.toFloat()
            val contentTop = page.info.contentRect.top.toFloat()
            val width = page.info.contentRect.width().toFloat()
            var y = drawHeader(page.canvas, contentLeft, contentTop, width)
            val bottom = page.info.contentRect.bottom.toFloat()
            val style = fittedRowStyle(width, bottom - y)

            items.forEach { item ->
                if (cancellationSignal.isCanceled) {
                    callback.onWriteCancelled()
                    return
                }
                val row = measuredRow(item, width, style)
                drawRow(page.canvas, contentLeft, y, row, style)
                y += row.height + style.rowGap
            }
            document.finishPage(page)

            FileOutputStream(destination.fileDescriptor).use(document::writeTo)
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (error: Exception) {
            callback.onWriteFailed(error.message ?: "Unable to create checklist.")
        } finally {
            document.close()
        }
    }

    private fun drawHeader(canvas: Canvas, left: Float, top: Float, width: Float): Float {
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = fittedTitleSize(width)
            isFakeBoldText = true
        }
        val baseline = top - titlePaint.fontMetrics.top
        canvas.drawText(title, left, baseline, titlePaint)
        val lineY = top + (titlePaint.fontMetrics.bottom - titlePaint.fontMetrics.top) + 8f
        canvas.drawLine(
            left,
            lineY,
            left + width,
            lineY,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.LTGRAY; strokeWidth = 1f },
        )
        return lineY + 12f
    }

    private fun fittedTitleSize(width: Float): Float {
        var size = 18f
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { isFakeBoldText = true }
        while (size > 10f) {
            paint.textSize = size
            if (paint.measureText(title) <= width) return size
            size -= 0.5f
        }
        return 10f
    }

    private fun fittedRowStyle(pageWidth: Float, availableHeight: Float): ChecklistRowStyle {
        var textSizePoints = 12f
        while (textSizePoints > 0.5f) {
            val style = rowStyle(textSizePoints)
            val requiredHeight = items.sumOf { measuredRow(it, pageWidth, style).height.toDouble() }.toFloat() +
                style.rowGap * (items.size - 1).coerceAtLeast(0)
            if (requiredHeight <= availableHeight) return style
            textSizePoints -= 0.25f
        }
        return rowStyle(0.5f)
    }

    private fun rowStyle(textSizePoints: Float): ChecklistRowStyle {
        val relative = textSizePoints / 12f
        return ChecklistRowStyle(
            textSize = textSizePoints,
            baseIndent = 24f * relative,
            indent = 24f * relative,
            boxSize = 11f * relative,
            textGap = 10f * relative,
            rowGap = 8f * relative,
            strokeWidth = maxOf(0.5f, relative),
        )
    }

    private fun measuredRow(item: ChecklistPrintItem, pageWidth: Float, style: ChecklistRowStyle): MeasuredChecklistRow {
        val indent = style.baseIndent + (item.indentLevel * style.indent)
        val textLeft = indent + style.boxSize + style.textGap
        val availableTextWidth = (pageWidth - textLeft).toInt().coerceAtLeast(1)
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = style.textSize
        }
        val textLayout = layout(item.name, paint, availableTextWidth)
        return MeasuredChecklistRow(
            indent = indent,
            boxSize = style.boxSize,
            textLeft = textLeft,
            textLayout = textLayout,
            height = maxOf(style.boxSize, textLayout.height.toFloat()),
        )
    }

    private fun drawRow(canvas: Canvas, left: Float, top: Float, row: MeasuredChecklistRow, rowStyle: ChecklistRowStyle) {
        val checkboxTop = top + ((row.height - row.boxSize) / 2f)
        canvas.drawRect(
            left + row.indent,
            checkboxTop,
            left + row.indent + row.boxSize,
            checkboxTop + row.boxSize,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                style = Paint.Style.STROKE
                strokeWidth = rowStyle.strokeWidth
            },
        )
        canvas.save()
        canvas.translate(left + row.textLeft, top)
        row.textLayout.draw(canvas)
        canvas.restore()
    }

    private fun layout(text: String, paint: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width.coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .build()

}

private data class MeasuredChecklistRow(
    val indent: Float,
    val boxSize: Float,
    val textLeft: Float,
    val textLayout: StaticLayout,
    val height: Float,
)

private data class ChecklistRowStyle(
    val textSize: Float,
    val baseIndent: Float,
    val indent: Float,
    val boxSize: Float,
    val textGap: Float,
    val rowGap: Float,
    val strokeWidth: Float,
)
