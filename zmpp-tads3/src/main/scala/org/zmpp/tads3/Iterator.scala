/*
 * Created on 2010/10/29
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

abstract class Iterator(id: TadsObjectId, vmState: TadsVMState)
extends TadsObject(id, vmState) {
  private val FunctionVector = Array(undef _, getNext _, isNextAvail _, resetIter _,
                                     getCurKey _, getCurVal _)

  def metaClass: MetaClass = objectSystem.iteratorMetaClass

  def undef(argc: Int): TadsValue = {
    throw new UnsupportedOperationException("undefined property")
  }
  def getNext(argc: Int): TadsValue = {
    throw new UnsupportedOperationException("undefined property")
  }
  def isNextAvail(argc: Int): TadsValue = {
    throw new UnsupportedOperationException("undefined property")
  }
  def resetIter(argc: Int): TadsValue = {
    throw new UnsupportedOperationException("undefined property")
  }
  def getCurKey(argc: Int): TadsValue = {
    throw new UnsupportedOperationException("undefined property")
  }
  def getCurVal(argc: Int): TadsValue = {
    throw new UnsupportedOperationException("undefined property")
  }
  override def findProperty(propertyId: Int): Property = {
    val idx = objectSystem.iteratorMetaClass.functionIndexForProperty(propertyId)
    throw new UnsupportedOperationException("undefined fun: " + propertyId +
                                            " idx = " + idx)
  }
}

class IndexedIterator(id: TadsObjectId, vmState: TadsVMState)
extends Iterator(id, vmState) {
  override def metaClass: MetaClass = objectSystem.indexedIteratorMetaClass
}

class LookupTableIteratorMetaClass extends MetaClass {
  def name = "lookuptable-iterator"
}

class IteratorMetaClass extends MetaClass {
  def name = "iterator"
}
class IndexedIteratorMetaClass extends MetaClass {
  def name = "indexed-iterator"
  def createIterator(coll: TadsCollection): IndexedIterator = {
    val id = objectSystem.newObjectId
    val iter = new IndexedIterator(id, vmState)
    objectSystem.registerObject(iter)
    iter
  }
}