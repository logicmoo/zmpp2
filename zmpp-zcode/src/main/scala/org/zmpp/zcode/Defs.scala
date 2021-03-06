/*
 * Created on 2010/05/12
 * Copyright (c) 2010-2012, Wei-ju Wu.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * Neither the name of Wei-ju Wu nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package org.zmpp.zcode

import scala.annotation.switch
import org.zmpp.base.VMRunStates
import org.zmpp.base.Memory
import org.zmpp.iff.QuetzalCompression

sealed class CapabilityFlag
case object SupportsColors      extends CapabilityFlag
case object SupportsUndo        extends CapabilityFlag
case object SupportsBoldFont    extends CapabilityFlag
case object SupportsItalicFont  extends CapabilityFlag
case object SupportsFixedFont   extends CapabilityFlag
case object SupportsTimedInput  extends CapabilityFlag
case object SupportsSound       extends CapabilityFlag
case object SupportsScreenSplit extends CapabilityFlag
case object SupportsMouse       extends CapabilityFlag
case object SupportsMenus       extends CapabilityFlag
case object SupportsPictures    extends CapabilityFlag

class StoryHeader(story: Memory) {
  private[this] var serial: Array[Byte] = null

  def version             = story.byteAt(0x00)
  def flags1              = story.byteAt(0x01)
  def releaseNumber       = story.shortAt(0x02)
  def himemStart          = story.shortAt(0x04)
  def startPC             = story.shortAt(0x06)
  def dictionary          = story.shortAt(0x08)
  def objectTable         = story.shortAt(0x0a)
  def globalVars          = story.shortAt(0x0c)
  def staticStart         = story.shortAt(0x0e)
  def flags2              = story.shortAt(0x10)
  def serialNumber        = {
    if (serial == null) {
      serial = new Array[Byte](6)
      var i = 0
      while (i < 6) {
        serial(i) = story.byteAt(0x12 + i).asInstanceOf[Byte]
        i += 1
      }
    }
    serial
  }
  def abbrevTable         = story.shortAt(0x18)
  def fileLength          = {
    val factor = if (version <= 3) 2
                 else if (version == 4 || version == 5) 4
                 else 8
    story.shortAt(0x1a) * factor
  }
  def checksum            = story.shortAt(0x1c)
  def terpNumber          = story.shortAt(0x1e) // V4
  def terpVersion         = story.shortAt(0x1f) // V4
  def screenHeight        = story.byteAt(0x20) // V4
  def screenWidth         = story.byteAt(0x21) // V4
  def screenWidthUnits    = story.shortAt(0x22) // V5
  def screenHeightUnits   = story.shortAt(0x24) // V5
  def fontWidthUnits      = {
    if (version == 6) story.byteAt(0x27)
    else story.byteAt(0x26)
  }
  def fontHeightUnits     = {
    if (version == 6) story.byteAt(0x26)
    else story.byteAt(0x27)
  }
  def routinesOffset      = story.shortAt(0x28)
  def staticStringsOffset = story.shortAt(0x2a)
  def defaultBackground   = story.shortAt(0x2c)
  def defaultForeground   = story.shortAt(0x2e)
  def pixelWidthInStream3_=(width: Int) = story.setShortAt(0x30, width)
  def standardRevision_=(revision: Int) = story.setShortAt(0x32, revision)
  def alphabetTable       = story.shortAt(0x34)
  def headerExtTable      = story.shortAt(0x36)
  def customAccentTable   = {
    val extTable = headerExtTable
    if (extTable > 0 && story.shortAt(extTable) >= 3) story.shortAt(extTable + 6)
    else 0
  }
  
  def unpackRoutineAddress(addr: Int) = {
    version match {
      case 1 => addr << 1
      case 2 => addr << 1
      case 3 => addr << 1
      case 4 => addr << 2
      case 5 => addr << 2
      case 6 => (addr << 2) + (routinesOffset << 3)
      case 7 => (addr << 2) + (routinesOffset << 3)
      case 8 => addr << 3
    }
  }
  def unpackStringAddress(addr: Int) = {
    version match {
      case 1 => addr << 1
      case 2 => addr << 1
      case 3 => addr << 1
      case 4 => addr << 2
      case 5 => addr << 2
      case 6 => (addr << 2) + (staticStringsOffset << 3)
      case 7 => (addr << 2) + (staticStringsOffset << 3)
      case 8 => addr << 3
    }
  }
  
  def isScoreGame = if (version < 3) true else (flags1 & 0x02) == 0

  // Yeah, "Beyond Zork" is weird
  def isBeyondZork = {
    isRevision(47, "870915") ||
    isRevision(49, "870917") ||
    isRevision(51, "870923") || 
    isRevision(57, "871221")
  }

  private def isRevision(release: Int, serial: String) = {
    releaseNumber == release && serial == new String(serialNumber)
  }
}

// cheap stack implementation. This stack holds int's, but the only int
// value that might get stored is the return address in the call frame
// (only happens on a push).
class Stack {
  private[this] val _values = new Array[Int](1024)
  private[this] var _sp = 0
  
  def sp = _sp
  def sp_=(value: Int) = _sp = value
  def push(value: Int) {
    _values(sp) = value
    _sp += 1
  }
  def pop = {
    _sp -= 1
    _values(_sp) & 0xffff
  }
  def top = _values(_sp - 1)
  def replaceTopWith(value: Int) {
    _values(_sp - 1) = value
  }

  // there is only one single case where we need this function: return
  // PCs.
  def value32At(index: Int) = _values(index)
  def valueAt(index: Int) = _values(index) & 0xffff // truncate to 16 bit
  def setValueAt(index: Int, value: Int) = _values(index) = value
  override def toString = {
    val builder = new StringBuilder
    for (i <- 0 until _sp) {
      builder.append("%d ".format(_values(i)))
    }
    builder.toString
  }

  def cloneValues : Array[Int] = {
    val values = new Array[Int](_sp)
    var i = 0
    while (i < _sp) {
      values(i) = _values(i)
      i += 1
    }
    values
  }

  def initFromArray(values: Array[Int]) {
    var i = 0
    while (i < values.length) {
      _values(i) = values(i)
      i += 1
    }
    _sp = values.length
  }
}

object FrameOffset {
  val ReturnPC     = 0
  val OldFP        = 1
  val StoreVar     = 2
  val NumArgs      = 3
  val NumLocals    = 4
  val Locals       = 5
  val NumInfoWords = 5
}

object ZMachineRunStates {
  val Halted       = VMRunStates.Halted
  val Running      = VMRunStates.Running
  val ReadLine     = VMRunStates.WaitForEvent
  val ReadChar     = 11
  val SaveGame     = 12
  val RestoreGame  = 13
}

/**
 * An undo snapshot of the vm state, the dynamic memory is compressed
 * using the same method as Quetzal to reduce memory footprint
 */
class Snapshot(val compressedDiff: Array[Byte], val stackValues: Array[Int],
               val pc: Int, val fp: Int)

trait VMState {
  def story: Memory
  def header: StoryHeader
  def encoding: ZsciiEncoding
  def runState: Int
  def pc: Int
  def pc_=(newpc: Int)

  def byteAt(addr: Int): Int
  def shortAt(addr: Int): Int
  def intAt(addr: Int): Int
  def setByteAt(addr: Int, value: Int)
  def setShortAt(addr: Int, value: Int)
  def setIntAt(addr: Int, value: Int)
}

class VMStateImpl extends VMState {
  import QuetzalCompression._

  private[this] var _story : Memory = null
  private[this] val _stack = new Stack

  var header     : StoryHeader   = null
  val encoding = new ZsciiEncoding(this)
  var runState = VMRunStates.Running
  var calculatedChecksum = 0
  // store the original dynamic memory as a reference point for
  // restart, undo snapshots and saving
  var originalDynamicMem: Array[Byte] = null

  var thrownAwayValue = 0
  var pc        = 0
  var fp        = 0 // frame pointer
  def sp        = _stack.sp
  def stack     = _stack
  def story     = _story
  def storyData = _story.buffer

  def reset {
    _stack.sp = 0
    // Set the initial frame pointer to -1. This is serving as a marker
    // when we search the stack to save
    fp        =  -1
    if (header.version != 6) {
      pc = header.startPC      
    } else {
      // V6 does function call to main routine
      call(header.startPC, null, -1, 0)
    }
    encoding.resetVMState
    // set interpreter information
    if (header.isBeyondZork) {
      // interpreter number set to 1 to indicate DECSystem-20
      // This has strong impact on Beyond Zork's output, which
      // becomes much more legible when set to DEC
      setByteAt(0x1e, 0x01)
      setByteAt(0x1f, '5'.asInstanceOf[Int])
    } else {
      setByteAt(0x1e, 0x04)
      setByteAt(0x1f, 6)
    }
    setShortAt(0x32, 0x0101)    
  }

  def reset(story: Memory) {
    _story = story
    header    = new StoryHeader(_story)
    saveOriginalDynamicMem

    // calculate the checksum before we make any in-memory modifications
    // but after we have a header
    calculatedChecksum = calculateChecksum
    reset
  }

  private def saveOriginalDynamicMem {
    val dynamicMemSize = header.staticStart
    originalDynamicMem = new Array[Byte](dynamicMemSize)
    System.arraycopy(storyData, 0, originalDynamicMem, 0, dynamicMemSize)
  }
  def restoreOriginalDynamicMem {
    System.arraycopy(originalDynamicMem, 0, storyData, 0, header.staticStart)
  }

  private def calculateChecksum = {
    var currentByteAddress = 0x40
    var checksum = 0
    //printf("CALC checksum, file size: %d, stored file size: %d\n",
    //       _story.size, header.fileLength)
    while (currentByteAddress < header.fileLength) {
      checksum += byteAt(currentByteAddress)
      currentByteAddress += 1
    }
    checksum & 0xffff
  }

  def byteAt(addr: Int)  = _story.byteAt(addr)
  def shortAt(addr: Int) = _story.shortAt(addr)
  def intAt(addr: Int)   = _story.intAt(addr)
  def setByteAt(addr: Int, value: Int)  = {
    if (addr >= header.staticStart) {
      throw new IllegalArgumentException("Attempt to write to static memory.")
    }
    _story.setByteAt(addr, value)
  }
  def setShortAt(addr: Int, value: Int) {
    if (addr >= header.staticStart) {
      throw new IllegalArgumentException("Attempt to write to static memory.")
    }
    _story.setShortAt(addr, value)
  }
  def setIntAt(addr: Int, value: Int)   = {
    if (addr >= header.staticStart) {
      throw new IllegalArgumentException("Attempt to write to static memory.")
    }
    _story.setIntAt(addr, value)
  }
  
  def nextByte = {
    pc += 1
    _story.byteAt(pc - 1)
  }
  def nextShort = {
    pc += 2
    _story.shortAt(pc - 2)
  }
  def stackEmpty = _stack.sp == 0
  def stackTop = _stack.top
  def replaceStackTopWith(value: Int) = _stack.replaceTopWith(value)
  def pushUserStack(userStack: Int, value: Int): Boolean = {
    val capacity = _story.shortAt(userStack)
    if (capacity == 0) false
    else {
      _story.setShortAt(userStack + capacity * 2, value)
      _story.setShortAt(userStack, capacity - 1)
      true
    }
  }
  def popUserStack(userStack: Int): Int = {
    val capacity = _story.shortAt(userStack)
    _story.setShortAt(userStack, capacity + 1)
    _story.shortAt(userStack + (capacity + 1) * 2)
  }

  def variableValue(varnum: Int) = {
    if (varnum == 0) _stack.pop
    else if (varnum >= 1 && varnum <= 15) { // local
      _stack.valueAt(fp + FrameOffset.Locals + (varnum - 1))
    } else { // global
      _story.shortAt(header.globalVars + ((varnum - 0x10) << 1))
    }
  }
  def setVariableValue(varnum: Int, value: Int) {
    if (varnum == 0) _stack.push(value)
    else if (varnum >= 1 && varnum <= 15) { // local
      //printf("SET L%02x TO %02x\n", varnum, value)
      _stack.setValueAt(fp + FrameOffset.Locals + (varnum - 1), value)
    } else if (varnum >= 16 && varnum <= 255) { // global
      _story.setShortAt(header.globalVars + ((varnum - 0x10) << 1), value)
    } else {
      // => throw away varnums < 0
      thrownAwayValue = value
    }
  }

  def nextOperand(operandType: Int) = {
    (operandType: @switch) match {
      case 0 => // large
        pc += 2
        _story.shortAt(pc - 2)
      case 1 => // small
        pc += 1
        _story.byteAt(pc - 1)
      case 2 => // var
        pc += 1
        variableValue(_story.byteAt(pc - 1))
    }
  }

  def call(packedAddr: Int, args: Array[Int], storeVar: Int, numArgs: Int) {
    if (packedAddr == 0) setVariableValue(storeVar, 0)
    else {
      val routineAddr = header.unpackRoutineAddress(packedAddr)
      val numLocals = _story.byteAt(routineAddr)
    
      // create a call frame
      val oldfp = fp
      fp = sp // current frame pointer
      _stack.push(pc) // return address
      _stack.push(oldfp)
      _stack.push(storeVar)
      _stack.push(numArgs)
      _stack.push(numLocals)
      pc = routineAddr + 1 // place PC after routine header

      var i = 0
      if (header.version <= 4) {
        while (i < numLocals) {
          _stack.push(nextShort)
          i += 1
        }
      } else {
        while (i < numLocals) {
          _stack.push(0)
          i += 1
        }
      }
      // set arguments to locals, throw away excess parameters (6.4.4.1)
      val numParams = if (numArgs <= numLocals) numArgs else numLocals
      i = 0
      while (i < numParams) {
        _stack.setValueAt(fp + FrameOffset.Locals + i, args(i))
        i += 1
      }
    }
  }
  def returnFromRoutine(retval: Int) {
    val retpc    = _stack.value32At(fp + FrameOffset.ReturnPC)
    val oldfp    = _stack.valueAt(fp + FrameOffset.OldFP)
    val storeVar = _stack.valueAt(fp + FrameOffset.StoreVar)
    _stack.sp = fp
    fp = oldfp
    pc = retpc
    setVariableValue(storeVar, retval)
  }

  def unwindStackToFramePointer(targetFramePointer: Int) {
    while (fp != targetFramePointer) {
      val oldfp = _stack.valueAt(fp + FrameOffset.OldFP)
      _stack.sp = fp
      fp = oldfp
    }
  }

  def numArgsCurrentRoutine = _stack.valueAt(fp + FrameOffset.NumArgs)
  def createSnapshot : Snapshot = {
    new Snapshot(compressDiffBytes(storyData, originalDynamicMem,
                                   header.staticStart),
                 _stack.cloneValues, pc, fp)
  }

  def readSnapshot(snapshot: Snapshot) {
    decompressDiffBytes(snapshot.compressedDiff, originalDynamicMem,
                        storyData, header.staticStart)
    _stack.initFromArray(snapshot.stackValues)
    pc = snapshot.pc
    fp = snapshot.fp
  }

  def setCapabilityFlags(flags: List[CapabilityFlag]) {
    var flags1 = byteAt(0x01)
    var flags2 = byteAt(0x10)
    flags.foreach(flag => flag match {
      case SupportsColors      => if (header.version >= 5) flags1 |= 0x01
      case SupportsPictures    => if (header.version == 6) flags1 |= 0x02
      case SupportsBoldFont    => if (header.version >= 4) flags1 |= 0x04
      case SupportsItalicFont  => if (header.version >= 4) flags1 |= 0x08
      case SupportsFixedFont   => if (header.version >= 4) flags1 |= 0x10
      case SupportsScreenSplit => if (header.version != 6) flags1 |= 0x20
      case SupportsSound       => if (header.version == 6) flags1 |= 0x20
      case SupportsTimedInput  => if (header.version >= 4) flags1 |= 0x80
      case _ => // do nothing
    })
    setByteAt(0x01, flags1)
    setByteAt(0x10, flags2)
  }
}

object Instruction {
  val FormLong           = 0
  val FormShort          = 1
  val FormVar            = 2
  val FormExt            = 3
  val OperandCountVar    = -1
  val OperandCountExtVar = -2
}

object OperandTypes {
  val LargeConstant = 0x00
  val SmallConstant = 0x01
  val Variable      = 0x02
  val Omitted       = 0x03
  
  def typeName(optype: Int) = optype match {
    case LargeConstant => "LargeConstant"
    case SmallConstant => "SmallConstant"
    case Variable      => "Variable"
    case Omitted       => "Omitted"
    case _             => "???"
  }
}

class DecodeInfo(var form: Int, var operandCount: Int, var opnum: Int,
                 var opcode: Int) {
  val types = new Array[Int](8)
  var numOperands = 0

  def set(f: Int, oc: Int, opn: Int, b0: Int) = {
    form         = f
    operandCount = oc
    opnum        = opn
    opcode       = b0
    this
  }
  override def toString = {
    opcodeName(5)
  }
  private def formName = {
    form match {
      case Instruction.FormLong  => "Long"
      case Instruction.FormShort => "Short"
      case Instruction.FormVar   => "Var"
      case Instruction.FormExt   => "Ext"
      case _         => "???"
    }
  }
  private def opCount = {
    if (operandCount == Instruction.OperandCountVar) "Var"
    else "%dOP".format(operandCount)
  }
  def isCallVx2 = {
    operandCount == Instruction.OperandCountVar &&
    (opnum == 0x1a || opnum == 0x0c)
  }

  def opcodeName(version: Int) = {
    operandCount match {
      case 0 => if (version >= 5 && opnum == 0x09) "CATCH" else Oc0OpNames(opnum)
      case 1 => if (version >= 5 && opnum == 0x0f) "CALL_1N" else Oc1OpNames(opnum)
      case 2 => Oc2OpNames(opnum)
      case Instruction.OperandCountVar    =>
        OcVarNames(opnum)
      case Instruction.OperandCountExtVar =>
        OcExtNames(opnum)
      case _         => "???"
    }
  }

  val Oc0OpNames = Array("RTRUE", "RFALSE", "PRINT", "PRINT_RET", "NOP", "SAVE", "RESTORE",
                         "RESTART", "RET_POPPED", "POP", "QUIT", "NEW_LINE", "SHOW_STATUS",
                         "VERIFY", "PIRACY")

  val Oc1OpNames = Array("JZ", "GET_SIBLING", "GET_CHILD", "GET_PARENT", "GET_PROP_LEN",
                         "INC", "DEC", "PRINT_ADDR", "CALL_1S", "REMOVE_OBJ", "PRINT_OBJ",
                         "RET", "JUMP", "PRINT_PADDR", "LOAD", "NOT")

  val Oc2OpNames = Array("???", "JE", "JL", "JG", "DEC_CHK", "INC_CHK", "JIN",
                         "TEST", "OR", "AND", "TEST_ATTR", "SET_ATTR", "CLEAR_ATTR",
                         "STORE", "INSERT_OBJ", "LOADW", "LOADB", "GET_PROP",
                         "GET_PROP_ADDR", "GET_NEXT_PROP", "ADD", "SUB", "MUL",
                         "DIV", "MOD", "CALL_2S", "CALL_2N", "SET_COLOUR", "THROW")

  val OcVarNames = Array("CALL", "STOREW", "STOREB", "PUT_PROP", "SREAD", "PRINT_CHAR",
                         "PRINT_NUM", "RANDOM", "PUSH", "PULL", "SPLIT_WINDOW",
                         "SET_WINDOW", "CALL_VS2", "ERASE_WINDOW", "ERASE_LINE",
                         "SET_CURSOR", "GET_CURSOR", "SET_TEXT_STYLE", "BUFFER_MODE",
                         "OUTPUT_STREAM", "INPUT_STREAM", "SOUND_EFFECT", "READ_CHAR",
                         "SCAN_TABLE", "NOT", "CALL_VN", "CALL_VN2", "TOKENISE",
                         "ENCODE_TEXT", "COPY_TABLE", "PRINT_TABLE", "CHECK_ARG_COUNT")

  val OcExtNames = Array("SAVE", "RESTORE", "LOG_SHIFT", "ART_SHIFT", "SET_FONT",
                         "DRAW_PICTURE", "PICTURE_DATA", "ERASE_PICTURE", "SET_MARGINS",
                         "SAVE_UNDO", "RESTORE_UNDO", "PRINT_UNICODE", "CHECK_UNICODE",
                         "MOVE_WINDOW", "WINDOW_SIZE", "WINDOW_STYLE", "GET_WIND_PROP",
                         "SCROLL_WINDOW", "POP_STACK", "READ_MOUSE", "MOUSE_WINDOW",
                         "PUSH_STACK", "PUT_WIND_PROP", "PRINT_FORM", "MAKE_MENU",
                         "PICTURE_TABLE")
}

class ReadLineInfo {
  var textBuffer        = 0
  var parseBuffer       = 0
  var maxInputChars     = 0
  var numLeftOverChars  = 0
  var routine           = 0
  var time              = 0
}

class ReadCharInfo {
  var routine = 0
  var time    = 0
}
