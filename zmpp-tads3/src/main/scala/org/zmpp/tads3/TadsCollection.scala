/*
 * Created on 2010/10/22
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

import java.util.ArrayList
import scala.collection.JavaConversions._
import org.zmpp.base._

abstract class TadsCollection(id: T3ObjectId, vmState: TadsVMState,
                              isTransient: Boolean)
extends AbstractT3Object(id, vmState, isTransient) {
  // because of polymorphism, metaClass can't be relied on to make a
  // static search, so we state the metaClass explicitly
  // this meta class is not inherited, so we can use it in static searches
  private def staticMetaClass: MetaClass = objectSystem.collectionMetaClass
  def metaClass: MetaClass = objectSystem.collectionMetaClass
  override def toString = "Collection object"
  def createIterator(argc: Int): T3Value
  def size: Int

  // for collections, we search the static property list, which is
  // in the object's meta class hierarchy
  override def getProperty(propertyId: Int, argc: Int): Property = {
    val idx = staticMetaClass.functionIndexForProperty(propertyId)
    printf("collection prop idx = %d\n", idx)
    val prop = staticMetaClass.callMethodWithIndex(this, idx, argc)
    if (prop != InvalidPropertyId) new Property(propertyId, prop, id)
    else super.getProperty(propertyId, argc)
  }
}

// Since both vectors and list share the fact that they are indexed
// collections, we implement the commonalities in the IndexedCollection
// class
abstract class IndexedCollection(id: T3ObjectId, vmState: TadsVMState,
                                 isTransient: Boolean)
extends TadsCollection(id, vmState, isTransient) {

  protected val _container = new ArrayList[T3Value]
  def size = _container.size
  def reverseSeq: Seq[T3Value] = _container.reverse
  def toSeq: Seq[T3Value]      = _container.toSeq 
  def initWith(seq: Seq[T3Value]) {
    seq.foreach(value => _container.add(value))
  }
  def append(value: T3Value) {
    _container.add(value)
  }
  override def valueAtIndex(index: T3Value): T3Value = _container(index.value - 1)
  override def setValueAtIndex(index: T3Value, newValue: T3Value): T3ObjectId = {
    val oldValue = _container(index.value - 1)
    _container(index.value - 1) = newValue
    id // return this object
  }
  def indexOf(value: T3Value): T3Value = {
    for (i <- 0 until _container.size) {
      if (objectSystem.t3vmEquals(_container(i), value)) return T3Integer(i + 1)
    }
    return T3Nil
  }

  def sort(desc: Boolean, compFunc: T3Value): T3Value = {
    printf("Vector.sort(desc = %b, fun = %s)\n", desc, compFunc)
    val seq = toSeq
    val sorted = if (compFunc != T3Nil) {
      seq.sortWith((val1, val2) => compareWithFun(compFunc, val1, val2, desc))
    } else {
      seq.sortWith((val1, val2) => {
        val compValue = objectSystem.compare(val1, val2)
        printf("val1 = %s, val2 = %s, compValue = %d\n", val1, val2, compValue)
        if (!desc && compValue < 0) true
        else if (desc && compValue > 0) true
        else false
      })
    }
    println("sorted: " + sorted)
    createNewFromSeq(sorted, false)
  }
  def createNewFromSeq(seq: Seq[T3Value], isTransient: Boolean): T3Value

  protected def compareWithFun(fun: T3Value,
                               val1: T3Value, val2: T3Value,
                               desc: Boolean): Boolean = {
    // note that we need to push the arguments in reverse order !!!
    vmState.stack.push(val2)
    vmState.stack.push(val1)
    new Executor(vmState).executeCallback(fun, 2)
    val compValue = vmState.r0.value
    if (!desc && compValue < 0) true
    else if (desc && compValue > 0) true
    else false
  }
}

/**
 * Function vector:
 * 0: undef
 * 1: createIterator()
 * 2: createLiveIterator()
 */
class CollectionMetaClass(objectSystem: ObjectSystem)
extends AbstractMetaClass(objectSystem) {
  def name = "collection"

  val FunctionVector = Array(undef _, createIterator _, createLiveIterator _)
  def undef(obj: T3Object, argc: Int): T3Value = InvalidPropertyId
  def createIterator(obj: T3Object, argc: Int): T3Value = {
    obj.asInstanceOf[TadsCollection].createIterator(argc)
  }
  def createLiveIterator(obj: T3Object, argc: Int): T3Value = {
    println("createLiveIterator")
    throw new UnsupportedOperationException("createLiveIterator")
  }

  override def callMethodWithIndex(obj: T3Object, index: Int,
                                   argc: Int): T3Value = {
    FunctionVector(index)(obj, argc)
  }
}
