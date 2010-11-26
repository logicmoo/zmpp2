/*
 * Created on 2010/11/23
 * Copyright (c) 2010, Wei-ju Wu.
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
package org.zmpp.tads3

import org.zmpp.base.Types
import org.zmpp.base.Memory
import org.zmpp.base.DefaultMemory

object RunStates {
  val Running = 1
  val Halted  = 0
}

object TadsVMState {
  // frame pointer offsets, note that they are "off by one" compared to
  // the reference implementation because our stack organiztion is
  // slightly different
  val FpOffsetArg1           = -9
  val FpOffsetTargetProp     = -8
  val FpOffsetOriginalTarget = -7
  val FpOffsetDefiningObject = -6
  val FpOffsetSelf           = -5
}

class TadsVMState(val objectSystem: ObjectSystem,
                  val functionSetMapper: IntrinsicFunctionSetMapper) {
  import TadsVMState._
  private var _memory : Memory = null
  var image: TadsImage         = null
  val stack                    = new Stack
  var runState                 = RunStates.Running
  // if callbackSP is >= 0, we are executing in a callback
  var callbackSP               = -1
  var startTime : Long         = 0
  objectSystem.vmState         = this
  
  // Registers (TODO: current savepoint, savepoint count)
  var r0: T3Value = T3Nil // data register R0
  var ip = 0              // instruction pointer
  var ep = 0              // current function entry pointer
  def sp = stack.sp       // stack pointer
  def sp_=(newsp: Int) {
    stack.sp = newsp
  } 
  var fp = 0                // frame pointer

  def reset(imageMem: Memory) {
    startTime = System.currentTimeMillis
    image     = new TadsImage(imageMem)
    objectSystem.reset
    image.readData(this)
    functionSetMapper.reset(this)

    // call initial function
    // push empty list on stack - QTads puts command line arguments in that list
    val argList = new T3ListConstant(0)
    stack.push(argList)
    doCall(1, image.startEntryPoint, 0,
           InvalidObjectId, InvalidObjectId, InvalidObjectId)

  }

  def nextCodeByte = {
    val retval = image.codeByteAt(ip)
    ip += 1
    retval
  }
  def nextCodeShort = {
    val retval = image.codeShortAt(ip)
    ip += 2
    retval
  }
  def nextCodeInt = {
    val retval = image.codeIntAt(ip)
    ip += 4
    retval
  }
  def dataHolderValueAt(addr: Int) = {
    val valueType = image.codeByteAt(addr)
    T3Value.create(valueType,
                   TypeIds.valueForType(valueType, image.codeIntAt(addr + 1)))
  }


  def doBranch {
    val branchOffset = Types.signExtend16(image.codeShortAt(ip))
    ip += branchOffset
  }

  def doReturn {
    sp = fp
    fp = stack.pop.value
    val argc = stack.pop.value
    ep = stack.pop.value
    val callerOffset = stack.pop.value
    // clean the rest of this invocation from the stack (self, definingObj,
    // origTargetObj, targetProp and args)
    for (i <- 0 until (argc + 4)) stack.pop
    if (callerOffset == 0) {
      throw new UnsupportedOperationException("TODO halt machine")
    }
    ip = ep + callerOffset

    // reset callback status to finished when we popped of its last call frame
    if (sp == callbackSP) callbackSP = -1
  }
  def doCall(argc: Int, targetOffs: Int,
             targetProp: Int, origTargetObj: T3ObjectId,
             definingObj: T3ObjectId, self: T3ObjectId) {

    val methodHeader = image.methodHeaderAt(targetOffs)
    r0 = T3Nil

    // allocate stack frame
    // The TM says push nil if there is no target property, the reference
    // implementation pushes property id 0, we do that, too, for the moment
    stack.pushPropertyId(targetProp)
    if (self == InvalidObjectId) { // VMInvalidObj
      stack.pushNil // target object
      stack.pushNil // defining object
      stack.pushNil // self object
    } else {
      stack.push(origTargetObj)
      stack.push(definingObj)
      stack.push(self)
    }
    // The spec says 'compute the offset from the current method header of
    // the next instruction execute and push the result'. This simply means
    // push CodeOfs(Instruction Pointer - Entry Pointer)
    stack.pushCodeOffset(ip - ep)
    stack.pushCodeOffset(ep)
    stack.pushInt(argc)
    stack.pushStackRef(fp)
    fp = sp
    
    // locals
    for (i <- 0 until methodHeader.localCount) stack.pushNil

    // new frame pointer
    ep = targetOffs
    ip = targetOffs + image.methodHeaderSize
  }
  
  // function argument acccess, indexing is 0-based
  def getParam(index: Int) = stack.valueAt(fp + FpOffsetArg1 - index)

  // local variable access. Note that Local variable access is based
  // on index 0 !!
  def getLocal(localNumber: Int) = stack.valueAt(fp + localNumber)
  def setLocal(localNumber: Int, value: T3Value) {
    stack.setValueAt(fp + localNumber, value)
  }
  def currentSelf = stack.valueAt(fp + FpOffsetSelf)
  def currentSelf_=(value: T3Value) = stack.setValueAt(fp + FpOffsetSelf, value)

  def targetProperty = {
    val prop = stack.valueAt(fp + FpOffsetTargetProp)
    if (prop == T3Nil) InvalidPropertyId else prop
  }
  def originalTarget = {
    val obj = stack.valueAt(fp + FpOffsetOriginalTarget)
    if (obj == T3Nil) InvalidObjectId else obj
  }
  def definingObject = {
    val obj = stack.valueAt(fp + FpOffsetDefiningObject)
    if (obj == T3Nil) InvalidObjectId else obj
  }
}

// Executors execute a given sequence of instructions. Since an Executor knows all
// the opcodes, it can both be used to implement the main execution control flow
// as well as the callbacks that are invoked by the intrinsic function sets.
// I believe that this is one of the cleaner solutions to realize the nested
// execution in TADS3
class Executor(vmState: TadsVMState) {
  val objectSystem           = vmState.objectSystem
  val functionSetMapper      = vmState.functionSetMapper
  var iteration              = 1

  def nextByteOperand   = vmState.nextCodeByte
  def nextShortOperand  = vmState.nextCodeShort
  def nextIntOperand    = vmState.nextCodeInt
  def nextSignedShortOperand = Types.signExtend16(nextShortOperand)
  def nextSignedByteOperand  = Types.signExtend8(nextByteOperand)
  def dataHolderValueAt(addr: Int)  = vmState.dataHolderValueAt(addr)

  def doTurn {
    while (vmState.runState == RunStates.Running) {
      executeInstruction
    }
  }
  
  def executeCallback(callback: T3Value, argc: Int) {
    printf("executeCallback(), %s, argc: %d\n", callback, argc)
    if (callback.valueType == TypeIds.VmObj) {
      // make sure the specified object defines "ObjectCallProp" and
      // invoke it
      val objectId = callback.asInstanceOf[T3ObjectId]
      val objectCallProp = vmState.image.symbolicNames("ObjectCallProp")
      val obj = vmState.objectSystem.objectWithId(callback.value)
      printf("handle object [%s], using objectCallProp: [%s]\n", obj, objectCallProp)
      val prop = obj.getProperty(objectCallProp.value, 0)
      printf("executeCallback() - PROP FOUND: %s\n", prop)
      if (prop.valueType == TypeIds.VmFuncPtr) {
        // Call the function
        // and execute the callback until we return to the same point
        // we can return when after a return
        // sp[aftercall] = sp[beforecall] - argc
        printf("SP BEFORE CALLBACK = %d\n", vmState.stack.sp)
        vmState.callbackSP = vmState.stack.sp - argc
        vmState.doCall(argc, prop.value, 0, objectId,
                       prop.definingObject, objectId)
        while (vmState.callbackSP >= 0) {
          executeInstruction
        }
        printf("SP AFTER CALLBACK = %d\n", vmState.stack.sp)
      } else {
        throw new IllegalArgumentException("ObjectCallProp is not a function pointer")
      }
    } else if (callback.valueType == TypeIds.VmFuncPtr) {
      throw new UnsupportedOperationException("funcptr callback TODO")
    } else {
      throw new IllegalArgumentException("unsupported callback type: " + callback)
    }
  }

  def executeInstruction {
    val opcode = vmState.nextCodeByte

    // debug
    printf("%04d: %s[%02x]\n", iteration, OpcodeNames.opcodeName(opcode), opcode)
    iteration += 1
    // debug

    import Opcodes._
    opcode match {
      case BP           =>
        throw new UnsupportedOperationException("Breakpoints not supported")
      case BuiltinA     =>
        functionSetMapper.callBuiltin(nextByteOperand, nextByteOperand, 0)
      case BuiltinB     =>
        functionSetMapper.callBuiltin(nextByteOperand, nextByteOperand, 1)
      case BuiltinC     =>
        functionSetMapper.callBuiltin(nextByteOperand, nextByteOperand, 2)
      case BuiltinD     =>
        functionSetMapper.callBuiltin(nextByteOperand, nextByteOperand, 3)
      case Builtin1     =>
        functionSetMapper.callBuiltin(nextByteOperand, nextByteOperand,
                                       nextByteOperand)
      case Builtin2     =>
        functionSetMapper.callBuiltin(nextByteOperand, nextShortOperand,
                                       nextByteOperand)
      case Call         =>
        vmState.doCall(nextByteOperand, nextIntOperand, 0,
                      InvalidObjectId, InvalidObjectId, InvalidObjectId)
      case CallProp     => callProp(nextByteOperand, vmState.stack.pop,
                                    nextShortOperand)
      case CallPropR0   => callProp(nextByteOperand, vmState.r0,
                                    nextShortOperand)
      case CallPropSelf => callProp(nextByteOperand, vmState.currentSelf,
                                    nextShortOperand)
      case Dup          => vmState.stack.dup
      case Eq           =>
        vmState.r0 = if (t3vmEquals(vmState.stack.pop, vmState.stack.pop)) T3True
                     else T3Nil
      case GetArg1      =>
        vmState.stack.push(vmState.getParam(nextByteOperand))
      case GetArg2      => vmState.stack.push(vmState.getParam(nextShortOperand))
      case GetLcl1      => vmState.stack.push(vmState.getLocal(nextByteOperand))
      case GetProp      => callProp(0, vmState.stack.pop, nextShortOperand)
      case GetPropLcl1  => callProp(0, vmState.getLocal(nextByteOperand), nextShortOperand)
      case GetPropR0    => callProp(0, vmState.r0, nextShortOperand)
      case GetPropSelf  => callProp(0, vmState.currentSelf, nextShortOperand)
      case GetR0        => vmState.stack.push(vmState.r0)
      case IdxInt8      => index(vmState.stack.pop, nextByteOperand)
      case IdxLcl1Int8  => index(vmState.getLocal(nextByteOperand), nextByteOperand)
      case Inc          => vmState.stack.push(add(vmState.stack.pop, T3Integer.One))
      case Jf           => branchIfTrue(!vmState.stack.pop.isTrue)
      case Jgt          =>
        // note the order of arguments, this is why we need to get them
        // explicitly
        val val2 = vmState.stack.pop
        val val1 = vmState.stack.pop
        branchIfTrue(compare(val1, val2) > 0)
      case Jmp          => vmState.doBranch
      case JNil         => branchIfTrue(vmState.stack.pop == T3Nil)
      case JNotNil      => branchIfTrue(vmState.stack.pop != T3Nil)
      case JR0T         => branchIfTrue(vmState.r0.isTrue)
      case JR0F         => branchIfTrue(!vmState.r0.isTrue)
      case Ne           =>
        vmState.r0 = if (!t3vmEquals(vmState.stack.pop, vmState.stack.pop)) T3True
                    else T3Nil
      case New1         =>
        vmState.r0 = vmState.objectSystem.createFromStack(nextByteOperand,
                                                        nextByteOperand, false)
      case Nop          => // do nothing
      case ObjCallProp  =>
        callProp(nextByteOperand, new T3ObjectId(nextIntOperand),
                 nextShortOperand)
      case ObjGetProp   => callProp(0, new T3ObjectId(nextIntOperand),
                                    nextShortOperand)
      case OneLcl1      => vmState.setLocal(nextByteOperand, T3Integer.One)
      case PtrCall      => ptrCall(nextByteOperand)
      case PtrInherit   => inheritProperty(nextByteOperand, vmState.stack.pop)
      case Push0        => vmState.stack.push0
      case Push1        => vmState.stack.push1
      case PushCtxEle   => pushCtxEle(nextByteOperand)
      case PushFnPtr    => vmState.stack.pushFunctionPointer(nextIntOperand)
      case PushEnum     => vmState.stack.pushEnum(nextIntOperand)
      case PushInt8     => vmState.stack.pushInt(nextSignedByteOperand)
      case PushNil      => vmState.stack.pushNil
      case PushObj      => vmState.stack.pushObjectId(nextIntOperand)
      case PushSelf     => vmState.stack.push(vmState.currentSelf)
      case PushTrue     => vmState.stack.push(T3True)
      case Ret          => vmState.doReturn
      case RetNil       =>
        vmState.r0 = T3Nil
        vmState.doReturn
      case RetTrue      =>
        vmState.r0 = T3True
        vmState.doReturn
      case RetVal       =>
        vmState.r0 = vmState.stack.pop
        vmState.doReturn
      case SetInd       =>
        val indexVal     = vmState.stack.pop
        val containerVal = vmState.stack.pop
        val newVal       = vmState.stack.pop
        throw new UnsupportedOperationException("SETIND not supported")
      case SetIndLcl1I8 =>
        val localNumber  = nextByteOperand
        val containerVal = vmState.getLocal(localNumber)
        val index        = nextByteOperand
        val newVal       = vmState.stack.pop
        vmState.setLocal(localNumber, setInd(containerVal, index, newVal))
      case SetLcl1      => vmState.setLocal(nextByteOperand, vmState.stack.pop)
      case SetLcl1R0    => vmState.setLocal(nextByteOperand, vmState.r0)
      case SetProp      =>
        objSetProp(vmState.stack.pop, nextShortOperand, vmState.stack.pop)
      case SetPropSelf  =>
        objSetProp(vmState.currentSelf, nextShortOperand,
                   vmState.stack.pop)
      case SetSelf      => vmState.currentSelf = vmState.stack.pop
      case Switch       =>
        val controlVal = vmState.stack.pop
        val caseCount  = nextShortOperand
        var p          = vmState.ip
        var counter    = 0
        var terminateLoop = false
        while (!terminateLoop) {
          val currval = dataHolderValueAt(p)
          if (controlVal.t3vmEquals(currval)) {
            val branchOffset = Types.signExtend16(
              vmState.image.codeShortAt(p + TadsConstants.SizeDataHolder))
            // note: the branch offset is calculated from the
            // address of the offset
            vmState.ip = p + TadsConstants.SizeDataHolder + branchOffset
            terminateLoop = true
          }
          counter += 1
          p       += TadsConstants.SizeDataHolder + 2 // + branch offset
          if (counter == caseCount) terminateLoop = true
        }
        if (counter == caseCount) {
          //  we did not find a value, branch to default
          val branchOffset = Types.signExtend16(vmState.image.codeShortAt(p))
          vmState.ip = p + branchOffset
        }
      case TrNew1       =>
        vmState.r0 = vmState.objectSystem.createFromStack(nextByteOperand,
                                                        nextByteOperand, true)
      case _            =>
        throw new UnsupportedOperationException("unknown opcode: 0x%02x"
                                                .format(opcode))
    }
    // DEBUGGING
    //if (iteration >= 791) {
      println("R0 = " + vmState.r0)
      //println(vmState.stack)
    //}
  }

  private def add(value1: T3Value, value2: T3Value): T3Value = {
    import TypeIds._
    if (value1.valueType == VmInt && value2.valueType == VmInt) {
      new T3Integer(value1.value + value2.value)
    } else if (value1.valueType == VmSString || value1.valueType == VmDString) {
      throw new UnsupportedOperationException("String.add not yet supported")
    } else if (value1.valueType == VmList) {
      throw new UnsupportedOperationException("List.add not yet supported")
    } else if (value1.valueType == VmObj) {
      throw new UnsupportedOperationException("Object.add not yet supported")
    } else throw new BadTypeAddException
  }

  // generic comparison function on TadsValues
  // used by conditional branches and comparison instructions
  // < 0 => value1 < value2
  //   0 => value1 == value2
  // > 0 => value1 > value2
  private def compare(value1: T3Value, value2: T3Value): Int = {
    import TypeIds._
    if (value1.valueType == VmInt && value2.valueType == VmInt) {
      value1.value - value2.value
    } else if ((value1.valueType == VmSString || value1.valueType == VmDString) &&
               (value2.valueType == VmSString || value2.valueType == VmDString)) {
      throw new UnsupportedOperationException("TODO string compare")
    } else if (value1.valueType == VmObj) {
      throw new UnsupportedOperationException("TODO object compare")
    } else throw new InvalidComparisonException
  }
  private def t3vmEquals(value1: T3Value, value2: T3Value): Boolean = {
    value1.t3vmEquals(value2)
  }

  // instruction implementations
  private def index(targetValue: T3Value, indexVal: Int) {
    if (targetValue.valueType == TypeIds.VmList) {
      throw new UnsupportedOperationException("indexing lists not supported yet")
    } else if (targetValue.valueType == TypeIds.VmObj) {
      val pushValue =
        vmState.objectSystem.objectWithId(targetValue).valueAtIndex(indexVal)
      vmState.stack.push(pushValue)
    } else throw new CannotIndexTypeException
  }

  private def ptrCall(argc: Int) {
    val stackVal = vmState.stack.pop
    if (stackVal.valueType == TypeIds.VmProp) {
      throw new UnsupportedOperationException("PtrCall with PROP not supported yet")
    } else if (stackVal.valueType == TypeIds.VmObj) {
      printf("OBJ PROP CALL, OBJ = %d\n", stackVal.value)
      val obj  = vmState.objectSystem.objectWithId(stackVal.value)
      val symb = vmState.image.symbolicNames("ObjectCallProp")
      if (symb != null && symb.valueType == TypeIds.VmProp) {
        printf("SYM: %s TYP: %d VAL: %d\n", symb.name, symb.valueType, symb.value)
        val prop = obj.getProperty(symb.value, argc)
        vmState.doCall(argc, prop.value, 0, obj.id, obj.id, obj.id)
      } else throw new FuncPtrValRequiredException
    } else if (stackVal.valueType == TypeIds.VmFuncPtr) {
      throw new UnsupportedOperationException("PtrCall with FuncPtr not supported yet")
    } else throw new FuncPtrValRequiredException
  }

  private def setInd(containerVal: T3Value, index: Int, newVal: T3Value) = {
    if (containerVal.valueType == TypeIds.VmObj) {
      val obj = vmState.objectSystem.objectWithId(containerVal.value)
      obj.setValueAtIndex(index, newVal)
    } else if (containerVal.valueType == TypeIds.VmList) {
      throw new UnsupportedOperationException("SETINDxxx not supported " +
                                              "for objects of list yet")
    } else throw new CannotIndexTypeException
  }

  private def branchIfTrue(condition: Boolean) {
    if (condition) vmState.doBranch
    else nextShortOperand // skip branch word
  }

  private def objSetProp(targetVal: T3Value, propId: Int,
                         newVal: T3Value) {
    if (targetVal.valueType == TypeIds.VmObj) {
      val obj = vmState.objectSystem.objectWithId(targetVal)
      obj.setProperty(propId, newVal)
    } else throw new ObjectValRequiredException
  }

  // one of the central functions of the VM: evaluating properties
  // the general strategy is to look up the property first
  // (pre-evaluation can happen here) and then evaluate the property
  // that was found. The reference implementation splits this into
  // a no-eval and an eval step, which is combined here for the moment
  // to see whether we find a factorization that fits better into the
  // Scala application context
  private def callProp(argc: Int, targetVal: T3Value, propId: Int) {
    printf("callProp(%s, %d, %d)\n", targetVal, argc, propId)

    if (targetVal.valueType == TypeIds.VmObj) {
      val obj = vmState.objectSystem.objectWithId(targetVal)
      printf("callProp(%s, %d, %d), obj: %s\n", targetVal, propId, argc, obj)
      val prop = obj.getProperty(propId, argc)
      if (prop != InvalidProperty) {
        printf("callProp() - Property found: %s\n", prop)
        evalProperty(targetVal.asInstanceOf[T3ObjectId], prop, argc)
      } else {
        // TODO: check if propNotDefined is available
        throw new UnsupportedOperationException("TODO: property not found, " +
                                                "check for propNotDefined")
      }
    } else if (targetVal.valueType == TypeIds.VmList) {
      // use constant list property evaluator
      // the targetValue is an offset into the list pool, not into the static
      // object pool !!!!
      if (argc > 0) throw new UnsupportedOperationException("callProp TODO list")

      val list = vmState.objectSystem.listConstantWithOffset(
        targetVal.asInstanceOf[T3ListConstant])
      val listMeta = vmState.objectSystem.metaClassForName("list")
      val result = listMeta.evalClassProperty(list, propId)
      vmState.r0 = result
    } else if (targetVal.valueType == TypeIds.VmSString ||
               targetVal.valueType == TypeIds.VmDString) {
      throw new UnsupportedOperationException("Cannot handle string constants yet")
    } else throw new ObjectValRequiredException
  }

  private def evalProperty(self: T3ObjectId, property: Property, argc: Int) {
    import TypeIds._
    printf("evalProperty(%s) [self = %s]\n", property, self)
    property.valueType match {
      case VmNil     => vmState.r0 = T3Nil
      case VmTrue    => vmState.r0 = T3True
      case VmObj     => vmState.r0 = new T3ObjectId(property.value)
      case VmProp    => vmState.r0 = new T3PropertyId(property.value)
      case VmInt     => vmState.r0 = new T3Integer(property.value)
      case VmList    => vmState.r0 = new T3ListConstant(property.value)
      case VmCodeOfs =>
        vmState.doCall(argc, property.value, property.id, self,
                      property.definingObject, self)
      case VmDString =>
        throw new UnsupportedOperationException("TODO: DOUBLE QUOTED STRING")
      case _ =>
        throw new UnsupportedOperationException(
          "UNHANDLED TYPE: %d => STORE %d IN R0\n".format(property.valueType,
                                                          property.value))
    }
  }

  private def inheritProperty(argc: Int, propId: T3Value) {
    val definingObject   = vmState.objectSystem.objectWithId(vmState.definingObject)
    printf("inheritProperty(%d, %s), defobj = %s\n", argc, propId, definingObject)
    val prop = definingObject.inheritProperty(propId.value, argc)
    printf("inheritProperty() - PROP FOUND: %s\n", prop)
    if (prop != InvalidProperty) {
      evalProperty(vmState.currentSelf.asInstanceOf[T3ObjectId], prop, argc)
    } else {
      // TODO: check if propNotDefined is available
      throw new UnsupportedOperationException("TODO: property not found, " +
                                              "check for propNotDefined")
    }
  }

  private def pushCtxEle(elem: Int) {
    printf("PUSHCTXELE, ELEM = %d HA\n", elem)
    elem match { // not reused, so I just use the constants directly
      case 1 => vmState.stack.push(vmState.targetProperty)
      case 2 => vmState.stack.push(vmState.originalTarget)
      case 3 => vmState.stack.push(vmState.definingObject)
      case _ => throw new IllegalArgumentException("elem: " + elem)
    }
  }
}