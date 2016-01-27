package latis.writer

import org.apache.poi.xssf.usermodel.XSSFRow
import org.apache.poi.xssf.usermodel.XSSFWorkbook

import latis.dm.Dataset
import latis.dm.Function
import latis.dm.Number
import latis.dm.Sample
import latis.dm.Text

class ExcelWriter extends Writer {
  
  //--- Excel File Properties -------------------------------------
                                                                       
  //Excel file's workbook                                                 
  val workbook = new XSSFWorkbook()                                       
                                                                          
  //Workbook's sheet                                                      
  val sheet = workbook.createSheet()                                   
                                                                       
  //Variables handle row/cell placement                                   
  var rowNum = 0                                                       
  var cellNum = 0                                                      
                                                                        
  //--- Write Operations ------------------------------------------
  
  /*
   * Method writes a single row of an excel sheet
   * by adding scalars into cells of that row.
   */
  def writeSample(s: Sample, row: XSSFRow) = {
    for (v <- s.toSeq) {
      val cell = row.createCell(cellNum)
      cellNum += 1
      
      v match {
        //TODO: Default cell alignment for Text is left-align
        //      Could this be improved?
        case Text(str) => cell.setCellValue(str)
        //TODO: Default cell alignment for Numbers is right-align
        //      Could this be improved?
        case Number(num) => {
          //Because num can be a NaN, if branch makes blank cells out of ones that      
          //would otherwise be filled with #NUM! (excel's invalid numeric value error)
          if (num.isNaN())
            cell.setCellValue("")
          else
            cell.setCellValue(num)
          } 
      }
    }
  }
  
  /*
   * Output the given Dataset as an Excel file (.xlsx)
   */
  def write(ds: Dataset) = {
    ds match {
      case Dataset(Function(iter)) => {
        for (sample <- iter) {
          val row = sheet.createRow(rowNum)
          rowNum += 1
          cellNum = 0
          
          writeSample(sample, row) 
        }
      }
    }
    
    try {
      val out = getOutputStream
      workbook.write(out)
      workbook.close
      out.close()
    } catch {
      case e: Exception => e.printStackTrace() //Consider doing something other than print?
    }
  }
  
  /*
   * Standard MIME type for Excel files
   */
  override def mimeType: String = "application/vnd.ms-excel"
  
}