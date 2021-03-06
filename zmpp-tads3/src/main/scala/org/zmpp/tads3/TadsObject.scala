/*
 * Created on 2010/10/12
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

import scala.collection.JavaConversions._
import scala.collection.mutable.Queue
import java.util.ArrayList
import org.zmpp.base._

// GenericObjects are instances of what the documentation calls "TADS Object".
// We wanted to avoid confusion, because "TadsObject" is the super class of
// all object classes in the ZMPP implementation. "GenericObjects" seems like
// a more fitting name which also more accurately reflects the purpose as
// a very flexible data structure the user can manipulate.
// This implementation currently just delegates to a static object for
// most cases, which is fine for now, but is not pure on-demand loading.
// We won't provide a different approach until for file-based loading,
// because of the need to enumerate all objects with some functions.
// We keep in mind that the generic object has almost a 1:1 representation
// in the load/save image.

// Image format for tads-object instances:
// UINT2 superclass_count
// UINT2 load_image_property_count
// UINT2 flags
// UINT4 superclass_1
// ...
// UINT4 superclass_N
// UINT2 load_image_property_ID_1
// DATAHOLDER load_image_property_value_1
// ...
// UINT2 load_image_property_ID_N
// DATAHOLDER load_image_property_value_N 
class TadsObject(id: T3ObjectId, vmState: TadsVMState,
                 override val isClassObject: Boolean,
                 superClassCount: Int,
                 propertyCount: Int,
                 isTransient: Boolean)
extends AbstractT3Object(id, vmState, isTransient) {
  def metaClass = objectSystem.tadsObjectMetaClass
  private def staticMetaClass = objectSystem.tadsObjectMetaClass

  // These should only be accessed from the MetaClass, we should find a better
  // way to access these later
  val superClassIds = new Array[Int](superClassCount)
  val properties    = new Array[Property](propertyCount)
  val extProperties = new ArrayList[Property]
  
  def numProperties = properties.length + extProperties.size

  override def toString = {
    "TadsObject[%s, isClassObject: %b, # super: %d, #props: %d]".format(
      id, isClassObject, superClassCount, propertyCount)
  }
  override def isInstanceOf(obj: T3Object): Boolean = {
    //printf("TadsObject.isInstanceOf() obj = %s\n", id)
    
    for (superClassId <- superClassIds) {
      printf("TadsObject.isInstanceOf() super = %d\n", superClassId)
      val superClassObj = objectSystem.objectWithId(superClassId)
      if (superClassObj == obj || superClassObj.isInstanceOf(obj)) return true
    }
    super.isInstanceOf(obj)
  }

  override def getProperty(propertyId: Int, argc: Int): Property = {
    val q = new Queue[TadsObject]
    q += this
    val prop = bfsSuperClasses(q, obj => {
      val prop = obj.findPropertyInThis(propertyId)
      if (prop != InvalidProperty) (true, prop) else (false, InvalidProperty)
    }, InvalidProperty)

    if (prop == InvalidProperty) {
      // not found in this or base classes -> search in the intrinsic class methods
      // (meaning we'll search the metaclass)
      val res = staticMetaClass.evalClassProperty(this, propertyId, argc)
      printf("RESULT FROM INTRINSIC: %s\n", res)
      if (res == InvalidPropertyId) InvalidProperty
      else new Property(propertyId, res, id)
    } else prop
  }

  // like getProperty(), but skip 'this'
  override def inheritProperty(propertyId: Int, argc: Int): Property = {
    val q = new Queue[TadsObject]
    for (superClassId <- superClassIds) {
      q += objectSystem.objectWithId(superClassId).asInstanceOf[TadsObject]
    }
    bfsSuperClasses(q, obj => {
      val prop = obj.findPropertyInThis(propertyId)
      if (prop != InvalidProperty) (true, prop) else (false, InvalidProperty)
    }, InvalidProperty)
  }

  private def findPropertyInThis(propertyId: Int): Property = {
    val found = properties.find(p => p.id == propertyId)
    if (found != None) {
      printf("(%s) FOUND PROP: %d in std props: %s\n", id, propertyId, found.get)
      return found.get
    }
    val foundExt = extProperties.find(p => p.id == propertyId)
    if (foundExt != None) {
      printf("(%s) FOUND PROP: %d in ext props: %s\n", id, propertyId, foundExt.get)
      foundExt.get 
    } else InvalidProperty
  }

  override def setProperty(propertyId: Int, newValue: T3Value) {
    val prop = findPropertyInThis(propertyId)
    if (prop == InvalidProperty) {
      printf("prop %d not found creating new one and setting to: %s (this: %s)\n",
             propertyId, newValue, this)
      val newProp = new Property(propertyId, newValue, this.id)
      extProperties.add(newProp)
    } else {
      printf("prop %d found updating existing one to: %s (this: %s)\n",
             propertyId, newValue, this)
      prop.tadsValue = newValue
    }
    // TODO: UNDO
  }

  override def ofKind(cls: T3ObjectId): T3Value = {
    val q = new Queue[TadsObject]
    for (superClassId <- superClassIds) {
      q += objectSystem.objectWithId(superClassId).asInstanceOf[TadsObject]
    }
    val foundInSuper = bfsSuperClasses(q, obj => {
      if (obj.id == cls) (true, T3True) else (false, T3Nil)
    }, T3Nil)
    if (foundInSuper.isTrue) foundInSuper
    else super.ofKind(cls)
  }

  // Generic search function to query the super class hierarchy of
  // TadsObject with a breadth-first search
  // Initialized with a queue containing the initial set of nodes
  // ('this' for regular searches, super class references for inherited
  // searches), the function 'pred' checks obj for a condition and
  // returns a pair of (found, result). If found is true, the BFS
  // search is terminated, if the search ends without a match, return
  // the default result
  private def bfsSuperClasses[T](q: Queue[TadsObject],
                                 pred: TadsObject => (Boolean, T),
                                 defaultResult: T): T = {
    while (!q.isEmpty) {
      val obj = q.dequeue
      val (found, currentResult) = pred(obj)
      printf("bfsSuperClasses(), obj = %s, found = %b, currentResult: %s\n", obj.id, found,
             currentResult)
      if (found) return currentResult

      for (superClassId <- obj.superClassIds) {
        q += objectSystem.objectWithId(superClassId).asInstanceOf[TadsObject]
      }
    }
    defaultResult
  }

  def createInstance(argc: Int): T3Value = {
    // self is the superclass
    vmState.stack.push(id)
    val obj = staticMetaClass.createFromStack(objectSystem.newObjectId,
                                              argc + 1, false)
    objectSystem.registerObject(obj)
    obj.id
  }

  def createTransientInstance(argc: Int): T3Value = {
    // self is the superclass
    vmState.stack.push(id)
    val obj = staticMetaClass.createFromStack(objectSystem.newObjectId,
                                              argc + 1, true, true)
    objectSystem.registerObject(obj)
    obj.id
  }
}

object TadsObjectMetaClass {
  val FlagIsClass = 0x0001
}
class TadsObjectMetaClass(objectSystem: ObjectSystem)
extends AbstractMetaClass(objectSystem) {
  def name = "tads-object"

  val FunctionVector = Array(undef _,            createInstance _,
                             createClone _,      createTransientInstance _,
                             createInstanceOf _, createTransientInstanceOf _,
                             setSuperClassList _)

  def undef(obj: T3Object, argc: Int): T3Value = InvalidPropertyId
  def createInstance(obj: T3Object, argc: Int): T3Value = {
    obj.asInstanceOf[TadsObject].createInstance(argc)
  }
  def createClone(obj: T3Object, argc: Int): T3Value = {
    throw new UnsupportedOperationException("createClone")
  }
  def createTransientInstance(obj: T3Object, argc: Int): T3Value = {
    obj.asInstanceOf[TadsObject].createTransientInstance(argc)
  }
  def createInstanceOf(obj: T3Object, argc: Int): T3Value = {
    throw new UnsupportedOperationException("createInstanceOf")
  }
  def createTransientInstanceOf(obj: T3Object, argc: Int): T3Value = {
    throw new UnsupportedOperationException("createTransientInstanceOf")
  }
  def setSuperClassList(obj: T3Object, argc: Int): T3Value = {
    throw new UnsupportedOperationException("setSuperClassList")
  }
  override def callMethodWithIndex(obj: T3Object, index: Int,
                                   argc: Int): T3Value = {
    FunctionVector(index)(obj, argc)
  }

  override def superMeta = objectSystem.rootObjectMetaClass
  override def createFromImage(objectId: T3ObjectId,
                               objDataAddr: Int,
                               numBytes: Int,
                               isTransient: Boolean): T3Object = {
    import TadsConstants._
    import TadsObjectMetaClass._
    val superClassCount = imageMem.shortAt(objDataAddr)
    val propertyCount   = imageMem.shortAt(objDataAddr + 2)
    val flags           = imageMem.shortAt(objDataAddr + 4)
    val isClassObject   = (flags & FlagIsClass) == FlagIsClass

    val tadsObject = new TadsObject(objectId, vmState,
                                    isClassObject, superClassCount,
                                    propertyCount, isTransient)
    for (index <- 0 until superClassCount) {
      tadsObject.superClassIds(index) = imageMem.shortAt(objDataAddr + 6 +
                                                         index * 4)
    }
    val propertyOffset = objDataAddr + 6 + superClassCount * 4
    for (index <- 0 until propertyCount) {
      val propAddr = propertyOffset + (DataHolder.Size + SizePropertyId) * index
      val propertyId    = imageMem.shortAt(propAddr)
      val propertyType  = imageMem.byteAt(propAddr + 2)
      val propertyValue = DataHolder.valueForType(propertyType,
                                                  imageMem.intAt(propAddr + 3))
      tadsObject.properties(index) =
        new Property(propertyId,
                     T3Value.create(propertyType, propertyValue),
                     objectId)
    }
    tadsObject
  }

  override def createFromStack(id: T3ObjectId, argc: Int,
                               isTransient: Boolean): T3Object = {
    createFromStack(id, argc, isTransient, false)
  }

  def createFromStack(id: T3ObjectId, argc: Int,
                      isTransient: Boolean,
                      fromCreateInstance: Boolean): T3Object = {
    printf("TadsObject.createFromStack(%d, %b)\n", argc, isTransient)
    var ctorArgc = argc
    val superClass = if (argc > 0) { ctorArgc -= 1 ; vmState.stack.pop } else T3Nil
    val superClassCount = if (superClass == T3Nil) 0 else 1
    val tadsObject = new TadsObject(id, vmState, false, superClassCount, 0,
                                    isTransient)
    val ctorProp = vmState.image.symbolicNames("Constructor").t3Value    
    if (superClassCount == 1) tadsObject.superClassIds(0) = superClass.value

    // if constructor defined, invoke it
    val ctor = tadsObject.getProperty(ctorProp.value, 0)
    printf("constructor is: %s\n", ctor)
    if (ctor.valueType == TypeIds.VmCodeOfs) {
      // invoke the constructor, either nested or non-nested. Since ZMPP
      // has the VM state accessible from every object and meta class,
      // we actually do not need to have a nested call, we just do it to
      // synchronize with the iteration numbers in the reference implementation
      if (fromCreateInstance) {
        // if we were called from createInstance(), we need to register the
        // object, because we are calling a nested invokation
        objectSystem.registerObject(tadsObject)
        val executor = new Executor(vmState)
        vmState.doNestedCall(executor, ctorArgc, ctor.value, ctor.id, tadsObject.id,
                             ctor.definingObject, tadsObject.id)
      } else {
        vmState.doCall(ctorArgc, ctor.value, ctor.id, tadsObject.id,
                       ctor.definingObject, tadsObject.id)
      }
    } else if (ctor != InvalidProperty) {
      throw new UnsupportedOperationException("unknown constructor type: " + ctor)
    }
    tadsObject
  }
}
