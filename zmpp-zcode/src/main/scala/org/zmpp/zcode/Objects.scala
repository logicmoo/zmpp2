/*
 * Created on 2010/05/12
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
package org.zmpp.zcode

/**
 * The object table is basically an updatable view into the Z-Machine's memory.
 * The concept separating into V1-V3 and V4-V8 object tables is kept from
 * ZMPP 1.x (this is actually just the Scala port of the ZMPP 1.x object table)
 */
abstract class ObjectTable(protected val _vm: Machine) {

  def insertObject(obj: Int, dest: Int) {
    val oldChild = child(dest)
    setParent(obj, dest)
    setChild(dest, obj)
    setSibling(obj, oldChild)
  }
  def parent(obj: Int): Int
  def setParent(obj: Int, newParent: Int)
  def child(obj: Int): Int
  def setChild(obj: Int, newChild: Int)
  def sibling(obj: Int): Int
  def setSibling(obj: Int, newSibling: Int)
  
  def isAttributeSet(obj: Int, attr: Int) = {
    val value = _vm.state.byteAt(attributeAddress(obj, attr))
    (value & (0x80 >> (attr & 7))) > 0
    //printf("testAttr obj: %d attr: %d objAddr = $%02x attrAddr = $%02x val = %d [%b] tableAddr = $%02x\n",
    //  obj, attr, objectAddress(obj), attributeAddress(obj, attr), value, result, objectTableAddress)
  }
  
  def setAttribute(obj: Int, attr: Int) {
    //throw new UnsupportedOperationException("objectTable.setAttribute() not implemented yet")
    val attrAddress = attributeAddress(obj, attr)
    val value = _vm.state.byteAt(attrAddress)
    _vm.state.setByteAt(attrAddress, value | (0x80 >> (attr & 7)))
  }
  def propertyTableAddress(obj: Int) = _vm.state.shortAt(objectAddress(obj) + objectEntrySize - 2)
  def propertyValue(obj: Int, prop: Int): Int = {
    val propAddr = propertyAddress(obj, prop)
    if (propAddr == 0) propertyDefault(prop)
    else {
      if (propertyLength(propAddr) == 1) _vm.state.byteAt(propAddr)
      else _vm.state.shortAt(propAddr) // 2 is assumed if longer, we just write two bytes
    }
  }

  private def propertyAddress(obj: Int, prop: Int): Int = {
    var propAddr = propertyEntriesStart(obj)
    while (true) {
      val propnum = propertyNum(propAddr)
      if (propnum == 0) return 0
      val numPropSizeBytes = numPropertySizeBytes(propAddr)
      if (propnum == prop) return propAddr + numPropSizeBytes
      propAddr += numPropSizeBytes + propertyLength(propAddr + numPropSizeBytes)
    }
    0
  }
  
  private def propertyEntriesStart(obj: Int) = {
    val propTableAddr = propertyTableAddress(obj)
    propTableAddr + (_vm.state.byteAt(propTableAddr) << 1) + 1
  }

  /*
  public int getPropertyAddress(final int objectNum, final int property) {
    int propAddr = getPropertyEntriesStart(objectNum);
    while (true) {
      int propnum = getPropertyNum(propAddr);
      if (propnum == 0) return 0; // not found
      if (propnum == property) {
        return propAddr + getNumPropertySizeBytes(propAddr);
      }
      int numPropBytes =  getNumPropertySizeBytes(propAddr);
      propAddr += numPropBytes + getPropertyLength(propAddr + numPropBytes);
    }
  }

  */
  
  // Protected members
  protected def objectTreeStart = objectTableAddress + propertyDefaultTableSize
  protected def objectTableAddress = _vm.state.header.objectTable
  protected def objectAddress(obj: Int) = objectTreeStart + (obj - 1) * objectEntrySize

  // abstract members
  protected def propertyDefaultTableSize: Int
  protected def objectEntrySize: Int
  protected def propertyNum(propAddr: Int): Int
  protected def propertyLength(propDataAddr: Int): Int
  protected def numPropertySizeBytes(propAddr: Int): Int
  
  // Private members
  private def attributeAddress(obj: Int, attr: Int) = objectAddress(obj) + attr / 8
  private def propertyDefault(prop: Int) = {
    _vm.state.shortAt(objectTableAddress + (prop << 1))
  }
}

class ClassicObjectTable(vm: Machine) extends ObjectTable(vm) {
  def parent(obj: Int)  = _vm.state.byteAt(objectAddress(obj) + 4)
  def setParent(obj: Int, newParent: Int) {
    _vm.state.setByteAt(objectAddress(obj) + 4, newParent)
  }
  def sibling(obj: Int) = _vm.state.byteAt(objectAddress(obj) + 5)
  def setSibling(obj: Int, newSibling: Int) = {
    _vm.state.setByteAt(objectAddress(obj) + 5, newSibling)
  }
  def child(obj: Int)   = _vm.state.byteAt(objectAddress(obj) + 6)
  def setChild(obj: Int, newChild: Int)   = {
    _vm.state.setByteAt(objectAddress(obj) + 6, newChild)
  }

  protected def propertyDefaultTableSize = 31 * 2
  protected def objectEntrySize          = 9

  protected def propertyNum(propAddr: Int) = {
    _vm.state.byteAt(propAddr) - 32 * (propertyLength(propAddr + 1) - 1)
  }  
  protected def propertyLength(propDataAddr: Int) = {
    if (propDataAddr == 0) 0 // Note: defined in Z-Machine Standard 1.1
    else {
      // The size byte is always the byte before the property data in any
      // version, so this is consistent
      _vm.state.byteAt(propDataAddr - 1) / 32 + 1
    }
  }
  protected def numPropertySizeBytes(propAddr: Int) = 1
/*  
  protected int getPropertyNum(final int propertyAddress) {
    final int sizeByte = getMemory().readUnsigned8(propertyAddress);
    return sizeByte - 32 * (getPropertyLength(propertyAddress + 1) - 1);
  }
  
    private static int getPropertyLengthAtData(final Memory memaccess,
                                             final int addressOfPropertyData) {
    if (addressOfPropertyData == 0) {
      return 0; // see standard 1.1
    }

    // The size byte is always the byte before the property data in any
    // version, so this is consistent
    final char sizebyte =
      memaccess.readUnsigned8(addressOfPropertyData - 1);

    return sizebyte / 32 + 1;
  }
*/
}

class ModernObjectTable(vm: Machine) extends ObjectTable(vm) {
  def parent(obj: Int)  = _vm.state.shortAt(objectAddress(obj) + 6)
  def setParent(obj: Int, newParent: Int) {
    _vm.state.setShortAt(objectAddress(obj) + 6, newParent)
  }
  def sibling(obj: Int) = _vm.state.shortAt(objectAddress(obj) + 8)
  def setSibling(obj: Int, newSibling: Int) = {
    _vm.state.setShortAt(objectAddress(obj) + 8, newSibling)
  }
  def child(obj: Int)   = _vm.state.shortAt(objectAddress(obj) + 10)    
  def setChild(obj: Int, newChild: Int)   = {
    _vm.state.setShortAt(objectAddress(obj) + 10, newChild)
  }

  protected def propertyDefaultTableSize = 63 * 2
  protected def objectEntrySize          = 14
  
  protected def propertyNum(propAddr: Int) = _vm.state.byteAt(propAddr) & 0x3f
  protected def propertyLength(propDataAddr: Int) = {
    if (propDataAddr == 0) 0 // Z-Machine Standard 1.1
    else {
      val sizeByte = _vm.state.byteAt(propDataAddr - 1)
      if ((sizeByte & 0x80) == 0x80) {
        val proplen = sizeByte & 0x3f
        if (proplen == 0) 64 // Standard 1.0 4.2.1.1
        else proplen
      } else {
        if ((sizeByte & 0x40) == 0x40) 2 else 1
      }
    }
  }
  protected def numPropertySizeBytes(propAddr: Int) = {
    if ((_vm.state.byteAt(propAddr) & 0x80) == 0x80) 2 else 1
  }

  /*
  protected int getPropertyNum(final int propertyAddress) {
    // Version >= 4 - take the lower 5 bit of the first size byte
    return getMemory().readUnsigned8(propertyAddress) & 0x3f;
  }

  protected int getNumPropertySizeBytes(final int propertyAddress) {
    // if bit 7 is set, there are two size bytes, one otherwise
    final char first = getMemory().readUnsigned8(propertyAddress);
    return ((first & 0x80) > 0) ? 2 : 1;
  }

  private static int getPropertyLengthAtData(final Memory memory,
                                            final int addressOfPropertyData) {
    if (addressOfPropertyData == 0) {
      return 0; // see standard 1.1
    }
    // The size byte is always the byte before the property data in any
    // version, so this is consistent
    final char sizebyte =
      memory.readUnsigned8(addressOfPropertyData - 1);

    // Bit 7 set => this is the second size byte
    if ((sizebyte & 0x80) > 0) {
      int proplen = sizebyte & 0x3f;
      if (proplen == 0) {
        proplen = 64; // Std. doc. 1.0, S 12.4.2.1.1
      }
      return proplen;
    } else {
      // Bit 7 clear => there is only one size byte, so if bit 6 is set,
      // the size is 2, else it is 1
      return (sizebyte & 0x40) > 0 ? 2 : 1;
    }
  }
   */
}
