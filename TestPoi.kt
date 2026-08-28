import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.FileOutputStream
import java.io.File
import java.io.OutputStream

class CountingOutputStream(private val delegate: OutputStream) : OutputStream() {
    var bytesWritten = 0L
    override fun write(b: Int) {
        delegate.write(b)
        bytesWritten++
    }
    override fun write(b: ByteArray) {
        delegate.write(b)
        bytesWritten += b.size
    }
    override fun write(b: ByteArray, off: Int, len: Int) {
        delegate.write(b, off, len)
        bytesWritten += len
    }
}

fun main() {
    val temp = File("test_export.xlsx")
    val workbook = XSSFWorkbook()
    val sheet = workbook.createSheet("Test")
    val row = sheet.createRow(0)
    row.createCell(0).setCellValue("Hello Excel")

    FileOutputStream(temp).use { fos ->
        CountingOutputStream(fos).use { countingOS ->
            workbook.write(countingOS)
        }
    }
    workbook.close()
    println("Saved Excel. Size: " + temp.length())
}
