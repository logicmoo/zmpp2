/*
 * Created on 2010/10/06
 * Copyright (c) 2010-2011, Wei-ju Wu.
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

// treat Java collections like Scala collections
// until we really understand the object system, we iterate in a similar
// way as the reference
// implementation (by ascending key order), so we need sorted maps.
// unfortunately, the Scala TreeMap is immutable, which might be fine for
// a lot of operations, but in an IF story, we might have frequent updates,
// which are potentially slow with an immutable structure.
// Later, the order will not matter, so we can replace this with a
// Scala HashMap
import scala.collection.JavaConversions._
import java.util.TreeMap
import org.zmpp.base._
import TypeIds._

// T3 object is the super interface of all objects in the TADS 3 system
// Note:
// The metaClass member defines the actual type of an object and is
// determined using Scala's/Java's normal inheritance mechanisms. We have
// to keep in mind that when we try to call super.getProperty(), the
// super implementation will in most cases need the non-polymorphic meta
// class, which is impossible to determine with the metaClass member.
// Therefore, each derived class defines a private member "staticMetaClass"
// in order to implement getProperty() bypassing polymorphism.
trait T3Object {
  def id: T3ObjectId
  def isTransient: Boolean
  def isClassObject : Boolean
  def metaClass: MetaClass
  def isOfMetaClass(meta: MetaClass): Boolean
  def isInstanceOf(obj: T3Object): Boolean
  def getProperty(propertyId: Int, argc: Int): Property
  def setProperty(propertyId: Int, newValue: T3Value)
  def valueAtIndex(index: T3Value): T3Value
  def setValueAtIndex(index: T3Value, newValue: T3Value): T3Value
  def inheritProperty(propertyId: Int, argc: Int): Property
  def t3vmEquals(other: T3Value): Boolean
  def +(other: T3Value): T3Value
  def -(other: T3Value): T3Value

  // RootObject methods
  def propInherited(prop: T3PropertyId, origTargetObj: T3ObjectId,
                    definingObj: T3ObjectId, flags: Int): T3Value
  def propDefined(propId: T3PropertyId, flags: Int): T3Value
  def ofKind(cls: T3ObjectId): T3Value
  def propType(propId: T3PropertyId): T3Value
}

// A null object for quick comparison
object InvalidObject extends AbstractT3Object(InvalidObjectId, null, false) {
  def metaClass = null
}

class Property(val id: Int, var tadsValue: T3Value,
               val definingObject: T3ObjectId) {
  def valueType = tadsValue.valueType
  def value = tadsValue.value
  override def toString = {
    "Property (id = %d value: %s def. obj: %s)".format(
      id, tadsValue, definingObject)
  }
}
object InvalidProperty extends Property(0, T3Nil, InvalidObjectId)

// Define all system meta classes that we know so far
// These are the current meta classes that are provided by the reference
// implementation. Apparently, the technical manual only mentions four of
// them, which makes it hard to implement a VM without looking at the
// QTads' source code.
// Instead of complaining, I'll just see how they are implemented in QTads and
// document it by myself
// Meta classes only inherit conceptually from each other, through the superMeta
// relationship, this makes it easier to selectively evaluate static class
// properties
trait MetaClass {
  // After being loaded, its dependency id and the VM state
  // can be directly accessed through its members.
  // This is so each object only needs to store the reference to its
  // meta class, but can still query the system state if necessary
  var id: Int
  def vmState: TadsVMState
  def name: String

  // instead of creating a parallel inheritance hierarchy of meta classes
  // we model the super relationship with aggregation. I think that evaluating
  // class properties feels cleaner this way
  def superMeta: MetaClass
  def reset
  def createFromStack(id: T3ObjectId, argc: Int,
                      isTransient: Boolean): T3Object
  def createFromImage(objectId: T3ObjectId, objDataAddr: Int,
                      numBytes: Int, isTransient: Boolean): T3Object
  def supportsVersion(version: String): Boolean
  def callMethodWithIndex(obj: T3Object, index: Int,
                          argc: Int): T3Value
  def evalClassProperty(obj: T3Object, propertyId: Int, argc: Int): T3Value

  // The static property map defines the mapping from a property id to
  // an index into the meta class's function table
  // (property id -> function vector index)
  // it should be cleared when loading a new image to
  // reload the mappings
  def addFunctionMapping(propertyId: Int, functionIndex: Int)
  def functionIndexForProperty(propertyId: Int): Int
}

// Note that while we could access the objectSystem through vmState, it
// is only available at image load time. For testing, it is better to
// have it available, so the creation methods can create object ids and
// register the objects. This also emphasizes the fact that meta classes
// are an enhancement of the object system
abstract class AbstractMetaClass(val objectSystem: ObjectSystem) extends MetaClass {
  private val propertyMap = new TreeMap[Int, Int]
  def reset = propertyMap.clear
  def superMeta: MetaClass = null
  def createFromStack(id: T3ObjectId,
                      argc: Int, isTransient: Boolean): T3Object = {
    throw new UnsupportedOperationException("createFromStack not yet " +
                                            "supported in " +
                                            "metaclass '%s'".format(name))
  }
  def createFromImage(objectId: T3ObjectId, objDataAddr: Int,
                      numBytes: Int, isTransient: Boolean): T3Object = {
    throw new UnsupportedOperationException("createFromImage not yet " +
                                            "supported in " +
                                            "metaclass '%s'".format(name))
  }
  def supportsVersion(version: String) = true

  def callMethodWithIndex(obj: T3Object, index: Int,
                          argc: Int): T3Value = {
    throw new UnsupportedOperationException(
      "%s: callMethodWithIndex not supported".format(name))
  }

  def evalClassProperty(obj: T3Object, propertyId: Int, argc: Int): T3Value = {
    var functionIndex = functionIndexForProperty(propertyId)
    val prop = callMethodWithIndex(obj, functionIndex, argc)
    if (prop == InvalidPropertyId && superMeta != null) {
      superMeta.evalClassProperty(obj, propertyId, argc)
    } else prop
  }
  var id: Int = 0
  def vmState = objectSystem.vmState
  def imageMem = vmState.image.memory
  def addFunctionMapping(propertyId: Int, functionIndex: Int) {
    printf("%s.addFunctionMapping(%d, %d)\n", name, propertyId, functionIndex)
    propertyMap(propertyId) = functionIndex
  }
  def functionIndexForProperty(propertyId: Int) = {
    if (propertyMap.containsKey(propertyId)) propertyMap(propertyId)
    else 0
  }
}

class IntClassModMetaClass(objectSystem: ObjectSystem)
extends AbstractMetaClass(objectSystem) {
  def name = "int-class-mod"
}
class ByteArrayMetaClass(objectSystem: ObjectSystem)
extends AbstractMetaClass(objectSystem) {
  def name = "bytearray"
  override def hashCode: Int = {
    throw new UnsupportedOperationException("TODO")
  }
}
class WeakRefLookupTableMetaClass(objectSystem: ObjectSystem)
extends AbstractMetaClass(objectSystem) {
  def name = "weakreflookuptable"
}

// Predefined symbols that the image defines. Can be accessed by the VM through
// the unique name
object PredefinedSymbols {
  val Constructor             = "Constructor"
  val Destructor              = "Destructor"
  val ExceptionMessage        = "exceptionMessage"
  val FileNotFoundException   = "File.FileNotFoundException"
  val FileCreationExcetpion   = "File.FileCreationException"
  val FileOpenException       = "File.FileOpenException"
  val FileIOException         = "File.FileIOException"
  val FileSyncException       = "File.FileSyncException"
  val FileClosedException     = "File.FileClosedException"
  val FileModeException       = "File.FileModeException"
  val FileSafetyException     = "File.FileSafetyException"
  val GpFirstTokenIndex       = "GrammarProd.firstTokenIndex"
  val GpLastTokenIndex        = "GrammarProd.lastTokenIndex"
  val GpTokenList             = "GrammarProd.tokenList"
  val GpTokenMatchList        = "GrammarProd.tokenMatchList"
  val GpGrammarAltInfo        = "GrammarProd.GrammarAltInfo"
  val GpGrammarAltTokInfo     = "GrammarProd.GrammarAltTokInfo"
  val IfcCalcHash             = "IfcComparator.calcHash"
  val IfcMatchValues          = "IfcComparator.matchValues"
  val LastProp                = "LastProp"
  val MainRestore             = "mainRestore"
  val ObjectCallProp          = "ObjectCallProp"
  val PropNotDefined          = "propNotDefined"
  val RuntimeError            = "RuntimeError"
  val T3StackInfo             = "T3StackInfo"
  val UnknownCharSetException = "CharacterSet.UnknownCharSetException"
}

// The object manager handles instantiation and management of objects.
// It is an extension of the VM state, because it represents the current state
// of all objects in the system.
// This version of the object manager has to be
// 1. reset before loading a new file
// 2. during loading the image, add metaclass dependencies as they come in
// 3. then load the classes as they come in
class ObjectSystem {
  // map the unique meta class names to the system meta classes
  // when initializing the game, this map can be used to map the image
  // identifiers for metaclass dependencies to the actual meta classes that
  // the ZMPP TADS3 VM supports
  val anonFuncPtrMetaClass         = new AnonFuncPtrMetaClass(this)
  val bigNumberMetaClass           = new BigNumberMetaClass(this)
  val byteArrayMetaClass           = new ByteArrayMetaClass(this)
  val characterSetMetaClass        = new CharacterSetMetaClass(this)
  val collectionMetaClass          = new CollectionMetaClass(this)
  val dictionary2MetaClass         = new Dictionary2MetaClass(this)
  val fileMetaClass                = new FileMetaClass(this)
  val grammarProductionMetaClass   = new GrammarProductionMetaClass(this)
  val indexedIteratorMetaClass     = new IndexedIteratorMetaClass(this)
  val intrinsicClassMetaClass      = new IntrinsicClassMetaClass(this)
  val intClassModMetaClass         = new IntClassModMetaClass(this)
  val iteratorMetaClass            = new IteratorMetaClass(this)
  val listMetaClass                = new ListMetaClass(this)
  val lookupTableMetaClass         = new LookupTableMetaClass(this)
  val lookupTableIteratorMetaClass = new LookupTableIteratorMetaClass(this)
  val regexPatternMetaClass        = new RegexPatternMetaClass(this)
  val rootObjectMetaClass          = new RootObjectMetaClass(this)
  val stringMetaClass              = new StringMetaClass(this)
  val stringComparatorMetaClass    = new StringComparatorMetaClass(this)
  val tadsObjectMetaClass          = new TadsObjectMetaClass(this)
  val vectorMetaClass              = new VectorMetaClass(this)
  val weakRefLookupTableMetaClass  = new WeakRefLookupTableMetaClass(this)

  val MetaClasses: Map[String, MetaClass] = Map(
    "tads-object"          -> tadsObjectMetaClass,
    "string"               -> stringMetaClass,
    "list"                 -> listMetaClass,
    "vector"               -> vectorMetaClass,
    "lookuptable"          -> lookupTableMetaClass,
    "dictionary2"          -> dictionary2MetaClass,
    "grammar-production"   -> grammarProductionMetaClass,
    "anon-func-ptr"        -> anonFuncPtrMetaClass,
    "int-class-mod"        -> intClassModMetaClass,
    "root-object"          -> rootObjectMetaClass,
    "intrinsic-class"      -> intrinsicClassMetaClass,
    "collection"           -> collectionMetaClass,
    "iterator"             -> iteratorMetaClass,
    "indexed-iterator"     -> indexedIteratorMetaClass,
    "character-set"        -> characterSetMetaClass,
    "bytearray"            -> byteArrayMetaClass,
    "regex-pattern"        -> regexPatternMetaClass,
    "weakreflookuptable"   -> weakRefLookupTableMetaClass,
    "lookuptable-iterator" -> lookupTableIteratorMetaClass,
    "file"                 -> fileMetaClass,
    "string-comparator"    -> stringComparatorMetaClass,
    "bignumber"            -> bigNumberMetaClass)

  private var _maxObjectId       = 0
  private val _objectCache       = new TreeMap[Int, T3Object]
  private val _metaClassMap      = new TreeMap[Int, MetaClass]
  private val _constantCache     = new TreeMap[Int, T3Object] 
  private def image: TadsImage   = vmState.image
  // public member
  var vmState: TadsVMState       = null

  def addMetaClassDependency(metaClassIndex: Int, nameString: String) {
    val name = nameString.split("/")(0)
    val version = if (nameString.split("/").length == 2) nameString.split("/")(1)
                      else "000000"
    _metaClassMap(metaClassIndex) = MetaClasses(name)
    _metaClassMap(metaClassIndex).reset
    _metaClassMap(metaClassIndex).id      = metaClassIndex
  }

  def addMetaClassPropertyId(metaClassIndex: Int, propertyIndex: Int,
                             propertyId: Int) {
    _metaClassMap(metaClassIndex).addFunctionMapping(propertyId, propertyIndex)
  }
  def addStaticObject(objectId: Int, metaClassIndex: Int,
                      objAddr: Int, numBytes: Int, isTransient: Boolean) {
    val id = T3ObjectId(objectId)
    val obj = _metaClassMap(metaClassIndex).createFromImage(id, objAddr,
                                                            numBytes, isTransient)
    registerObject(obj)
    // set the maximum object id higher so that after we loaded all
    // static objects, we have a start object id
    if (objectId > _maxObjectId) _maxObjectId = objectId + 1
  }

  def reset {
    _maxObjectId = 0
    _metaClassMap.clear
    _objectCache.clear
    _constantCache.clear
  }

  // create a unique object id
  // we assume that a VM runs single-threaded and we never reuse ids
  private def newId = {
    _maxObjectId += 1
    _maxObjectId
  }
  def newObjectId = T3ObjectId(newId)
  def registerObject(obj: T3Object) {
    _objectCache(obj.id.value) = obj
  }
  def registerConstant(offset: T3Value, obj: T3Object) {
    _constantCache(offset.value) = obj
  }
  def createFromStack(argc: Int, metaClassId: Int, isTransient: Boolean) = {
    val id = T3ObjectId(newId)
    printf("%s.createFromStack()\n", _metaClassMap(metaClassId))
    val obj = _metaClassMap(metaClassId).createFromStack(id, argc, isTransient)
    _objectCache(id.value) = obj
    id
  }

  def printMetaClasses {
    for (i <- _metaClassMap.keys) {
      printf("ID: %d NAME: %s\n", i, _metaClassMap(i).name)
    }
  }

  // **********************************************************************
  // **** Query Functions
  // **********************************************************************

  def metaClassForIndex(index: Int): MetaClass = _metaClassMap(index)
  def metaClassForName(name: String): MetaClass = MetaClasses(name)
  def objectWithId(id: Int): T3Object = {
    if (_objectCache.contains(id)) _objectCache(id)
    else {
      printf("object not found in cache: %d\n", id)
      throw new ObjectNotFoundException
    }
  }
  def objectWithId(id: T3Value): T3Object = objectWithId(id.value)
  def listConstantWithOffset(offset: T3ListConstant) = {
    if (_constantCache.containsKey(offset.value))
      _constantCache(offset.value).asInstanceOf[TadsListConstant]
    else listMetaClass.createListConstant(offset)
  }
  def stringConstantWithOffset(offset: T3SString) = {
    if (_constantCache.containsKey(offset.value))
      _constantCache(offset.value).asInstanceOf[TadsStringConstant]
    else
      stringMetaClass.createStringConstant(offset)
  }

  def isList(value: T3Value): Boolean = value.valueType match {
      case VmList => true
      case VmObj  => objectWithId(value).isOfMetaClass(listMetaClass)
      case _      => false
  }

  // Enumeration of objects
  def firstObject(enumParams: EnumObjectParams): T3Object = {
    nextObject(InvalidObjectId, enumParams)
  }
  def nextObject(prevObject: T3ObjectId, enumParams: EnumObjectParams): T3Object = {
    var previousFound   = false
    for (entry <- _objectCache) { // entries are pairs of (key, value)
      val currentObj      = entry._2
      var shouldBeChecked = true
      if (!enumParams.enumInstances && !currentObj.isClassObject) shouldBeChecked = false
      if (!enumParams.enumClasses && currentObj.isClassObject) shouldBeChecked = false

      // handle previous object detection
      if (prevObject != InvalidObjectId && !previousFound) shouldBeChecked = false
      if (prevObject != InvalidObjectId && currentObj.id == prevObject) {
        printf("PREVIOUS FOUND !!!!\n")
        previousFound = true
      }
      // TODO: ignore list and string objects
      if (shouldBeChecked) {
        printf("ObjectSystem.nextObject(enumInstances = %b, enumClasses = %b), currentObj: %s\n", enumParams.enumInstances, enumParams.enumClasses, currentObj.id)
        if (enumParams.matchClass == InvalidObject) return currentObj
        if (currentObj.isInstanceOf(enumParams.matchClass)) return currentObj
      }
    }
    InvalidObject
  }

  def toT3Object(value: T3Value): T3Object = {
    if (value.valueType == VmSString) {
      stringConstantWithOffset(value.asInstanceOf[T3SString])
    } else if (value.valueType == VmList) {
      listConstantWithOffset(value.asInstanceOf[T3ListConstant])
    } else if (value.valueType == VmObj) {
      objectWithId(value.asInstanceOf[T3ObjectId])
    } else {
      throw new IllegalArgumentException("unsupported data type for value")
    }
  }

  def toTadsString(value: T3Value): TadsString = {
    toT3Object(value).asInstanceOf[TadsString]
  }
  def toTadsList(value: T3Value): TadsList = {
    toT3Object(value).asInstanceOf[TadsList]
  }

  // the t3vmEquals method is defined here so we can call it from anywhere
  def t3vmEquals(value1: T3Value, value2: T3Value): Boolean = {
    if (value1.valueType == VmObj) {
      objectWithId(value1.asInstanceOf[T3ObjectId]).t3vmEquals(value2)
    } else if (value1.valueType == VmSString) {
      toT3Object(value1).t3vmEquals(value2)
    } else value1.t3vmEquals(value2)
  }

  // generic comparison function on TadsValues
  // used by conditional branches and comparison instructions
  // < 0 => value1 < value2
  //   0 => value1 == value2
  // > 0 => value1 > value2
  def compare(value1: T3Value, value2: T3Value): Int = {
    if (value1.valueType == VmInt && value2.valueType == VmInt) {
      value1.value - value2.value
    } else if ((value1.valueType == VmSString || value1.valueType == VmDString) &&
               (value2.valueType == VmSString || value2.valueType == VmDString)) {
      throw new UnsupportedOperationException("TODO string compare")
    } else if (value1.valueType == VmObj) {
      throw new UnsupportedOperationException("TODO object compare")
    } else throw new InvalidComparisonException
  }
}

class EnumObjectParams(val matchClass: T3Object, val enumInstances: Boolean,
                       val enumClasses: Boolean) {
  override def toString = {
    "EnumObjectParams[matchClass: %s, enumInstances: %b, enumClasses: %b]".format(
      matchClass, enumInstances, enumClasses)
  }
}
