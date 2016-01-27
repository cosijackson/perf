package latis.reader.tsml

import scala.collection.Iterator
import scala.collection.JavaConversions.asScalaIterator
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.ArrayBuffer

import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory

import latis.data.Data
import latis.reader.tsml.ml.Tsml
import latis.util.StringUtils

class ExcelAdapter(tsml: Tsml) extends IterativeAdapter2[Row](tsml) {

  //---- Manage data source ---------------------------------------------------

  private lazy val excelStream = getUrl.openStream()

  //---- Adapter Properties ---------------------------------------------------

  //Workbook created from the input stream.
  //Both excel file types are supported via WorkbookFactory (.xls or .xlsx)
  val workbook = WorkbookFactory.create(excelStream)
  
  /**
   * Return the number of lines (as returned by Source.getLines) that
   * should be skipped before reading data.
   */
  def getLinesToSkip: Int = getProperty("skip") match {
    case Some(s) => s.toInt
    case None => 0
  }

  //---- Parse operations -----------------------------------------------------

  /**
   * Method used to get an iterator of Rows.
   */
  def getRecordIterator: Iterator[Row] = {

    //Get desired sheet from workbook,
    val sheet = workbook.getSheetAt(0) //TODO: Consider supporting files with multiple sheets

    //Close workbook,
    workbook.close()

    //Return the iterator of Rows from the sheet.
    val skipLines = getLinesToSkip
    sheet.iterator().drop(skipLines)
  }

  /*
   * Returns a List of the contents of every cell of the given iterator.
   */
  def parseCells(cells: Iterator[Cell]) = {

    //cellContents gets returned after a toList conversion 
    var cellContents = new ListBuffer[Any]
    
    //Loop through all cells and add contents to the list
    while (cells.hasNext) {
      val cell: Cell = cells.next()

      cell.getCellType() match {
        case Cell.CELL_TYPE_NUMERIC =>
          cellContents += cell.getNumericCellValue
        case Cell.CELL_TYPE_STRING => 
          cellContents += cell.getStringCellValue
        case Cell.CELL_TYPE_FORMULA => {
          //Determine the type of formula and extract accordingly
          val evaluator = workbook.getCreationHelper.createFormulaEvaluator()
          val cellValue = evaluator.evaluate(cell)
          
          cellValue.getCellType() match {
            case Cell.CELL_TYPE_BOOLEAN =>
              cellContents += cellValue.getBooleanValue
            case Cell.CELL_TYPE_NUMERIC =>
              cellContents += cellValue.getNumberValue
            case Cell.CELL_TYPE_STRING =>
              cellContents += cellValue.getStringValue
            case Cell.CELL_TYPE_BLANK =>
              cellContents += cellValue.getStringValue 
            case Cell.CELL_TYPE_ERROR =>
              cellContents += cellValue.getErrorValue
            //CELL_TYPE_FORMULA will never happen
          }			
        } 
        case Cell.CELL_TYPE_BLANK =>
          cellContents += cell.getStringCellValue 
        case Cell.CELL_TYPE_ERROR =>
          cellContents += cell.getErrorCellValue
        case Cell.CELL_TYPE_BOOLEAN =>
          cellContents += cell.getBooleanCellValue
      }
    }

    //Return the list of cell contents
    cellContents.toList
  }
  
  def parseArrayOfCells(cells: Array[Cell]) = {
    var cellContents = new ListBuffer[Any]
    
    //Loop through all cells and add contents to the list
    for (i <- 0 to cells.length-1) {
      val cell: Cell = cells(i)

      cell.getCellType() match {
        case Cell.CELL_TYPE_NUMERIC =>
          cellContents += cell.getNumericCellValue
        case Cell.CELL_TYPE_STRING => 
          cellContents += cell.getStringCellValue
        case Cell.CELL_TYPE_FORMULA => {
          //Determine the type of formula and extract accordingly
          val evaluator = workbook.getCreationHelper.createFormulaEvaluator()
          val cellValue = evaluator.evaluate(cell)
          
          cellValue.getCellType() match {
            case Cell.CELL_TYPE_BOOLEAN =>
              cellContents += cellValue.getBooleanValue
            case Cell.CELL_TYPE_NUMERIC =>
              cellContents += cellValue.getNumberValue
            case Cell.CELL_TYPE_STRING =>
              cellContents += cellValue.getStringValue
            case Cell.CELL_TYPE_BLANK =>
              cellContents += cellValue.getStringValue 
            case Cell.CELL_TYPE_ERROR =>
              cellContents += cellValue.getErrorValue
            //CELL_TYPE_FORMULA will never happen
          }			
        } 
        case Cell.CELL_TYPE_BLANK =>
          cellContents += cell.getStringCellValue 
        case Cell.CELL_TYPE_ERROR =>
          cellContents += cell.getErrorCellValue
        case Cell.CELL_TYPE_BOOLEAN =>
          cellContents += cell.getBooleanCellValue
      }
    }

    //Return the list of cell contents
    cellContents.toList.toArray.map(_.toString)
  }

  def getCellContentsFromRow(record: Row): Array[String] = {
    //var cellContents = new ArrayBuffer[String]
    var cellContents = new ArrayBuffer[Cell]
    for (i <- 0 to record.getLastCellNum) {
      val cell = record.getCell(i, Row.CREATE_NULL_AS_BLANK)
      cellContents += cell //I'm trying to get all cell contents, so need to check how blanks are handled here
    }
    
    parseArrayOfCells(cellContents.toArray)
  }
  
  /**
   * Extracts the cell values in each Row.
   * Returns Map with Variable name to value(s) as Data.
   */
  def parseRecord(record: Row): Option[Map[String, Data]] = {

    //Create a sequence of strings out of cell contents 
    //TODO: Consider if/how string conversion affects cell contents 
  
    lazy val values: Seq[String] = getProperty("columns") match {
    case Some(s: String) => {
      s.split(";").map(p => p.split(",").map(_.toInt))
     
    val ss = getCellContentsFromRow(record)
    if(ss.length < values.flatten.max) List() //Ignore rows with fewer columns than those requested
    else values.map(is => is.map(ss(_)).mkString(" ")) //append with " " for now since delimiter could be a regex
  
    }
    case None => parseCells(record.cellIterator()).toSeq.map(_.toString) //cellIterator considers blank cells as null
  }
    

    //if branch checks for rows of empty cells,
    //parses everything else.
    if (values.isEmpty) {
      None
    } else {

      val vars = getOrigScalars

      val vnames: Seq[String] = vars.map(_.getName)
      val datas: Seq[Data] = (values zip vars).map(p => {
        val value = tsml.findVariableAttribute(p._2.getName, "regex") match {//look for regex as tsml attribute
          case Some(s) => s.r.findFirstIn(p._1) match {                      //try to match the value with the regex
            case Some(m) => m                                                //use the matching part
            case None => p._2.getFillValue.toString                          //or use a fill value since this doesn't match
          }
          case None => p._1                                                  //no regex pattern to match so use the original value
        }
        //convert the data values to Data objects using the Variable as a template
        StringUtils.parseStringValue(value, p._2)
      })

      Some((vnames zip datas).toMap)
    }
  }

  /**
   * Closes the input stream
   */
  def close() = {
    excelStream.close()
  }
}