/*
 * Created on 2010/10/13
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
import java.util.ArrayList
import org.zmpp.base._

/*
 * An instance of IntrinsicClass represents an intrinsic meta class within
 * the TadsObject hierarchy. This is because intrinsic metaclasses are
 * objects of the VM, while TadsObject's exist in the game space. The load
 * image specifies the mapping between intrinsic VM objects and game objects
 * which is established at image load time.
 * We just follow the scheme of the reference implementation here.
 */
class IntrinsicClass(id: TadsObjectId, metaClass: MetaClass,
                     val representedMetaClass: MetaClass,
                     val modifierObjId: Int)
extends TadsObject(id, metaClass) {
  override def isClassObject = true
  override def isInstanceOf(obj: TadsObject): Boolean = {
    throw new UnsupportedOperationException("not implemented yet")
  }
}

// Image data format of an intrinsic-class entry
// UINT2 byte_count of the data block (currently 8)
// UINT2 metaclass_dependency_table_index
// UINT4 modifier_object_id
class IntrinsicClassMetaClass extends MetaClass {
  def name = "intrinsic-class"
  override def createFromImage(objectManager: ObjectManager,
                               imageMem: Memory, objectId: Int,
                               objDataAddr: Int,
                               numBytes: Int,
                               isTransient: Boolean): TadsObject = {
    val byteCount      = imageMem.shortAt(objDataAddr)
    val metaClassIndex = imageMem.shortAt(objDataAddr + 2)
    val modifierObjId  = imageMem.intAt(objDataAddr + 4)
    println("-------------------------------------------------------------")
    printf("CREATING INTRINSIC CLASS %d, # BYTES: %d, METACLASS: %d " +
           "MODIFIER OBJ: %d\n",
           objectId, byteCount, metaClassIndex, modifierObjId)
    println("-------------------------------------------------------------")
    new IntrinsicClass(new TadsObjectId(objectId), this,
                       objectManager.metaClassForIndex(metaClassIndex),
                       modifierObjId)
    // TODO: Assign this object to the metaclass
  }
}