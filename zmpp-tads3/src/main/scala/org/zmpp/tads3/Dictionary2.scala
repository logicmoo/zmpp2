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

import java.util.ArrayList
import scala.collection.JavaConversions._
import org.zmpp.base._

// The image file data block is arranged as follows:
  
// UINT4 comparator_object_id
// UINT2 load_image_entry_count
// entry 1
// entry 2
// ...
// entry N

// Each entry has the following structure:

// UCHAR key_string_byte_length
// key_string (UTF-8 characters, not null terminated, XOR'ed with 0xBD)
// UINT2 number of sub-entries
// sub-entry 1
// sub-entry 2
// etc

// Each sub-entry is structured like this:

// UINT4 associated_object_id
// UINT2 defining_property_id

// Note that each byte of the key string is XOR'ed with the arbitrary
// byte value 0xBD.  This is simply to provide a minimal level of
// obfuscation in the image file to prevent casual browsing of the image
// contents.

class Dictionary2(id: TadsObjectId, metaClass: MetaClass)
extends TadsObject(id, metaClass) {
}

class Dictionary2MetaClass extends MetaClass {
  def name = "dictionary2"
  override def createFromImage(objectManager: ObjectManager,
                               imageMem: Memory, objectId: Int,
                               objDataAddr: Int,
                               numBytes: Int,
                               isTransient: Boolean): TadsObject = {
    val dictionary = new Dictionary2(new TadsObjectId(objectId), this)
    dictionary
  }
}