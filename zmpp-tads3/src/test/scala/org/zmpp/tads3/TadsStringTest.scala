/*
 * Created on 2010/11/28
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

import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.BeforeAndAfterEach

@RunWith(classOf[JUnitRunner])
class TadsStringSpec extends FlatSpec with ShouldMatchers with BeforeAndAfterEach {

  var objectSystem : ObjectSystem = null
  var functionSetMapper : IntrinsicFunctionSetMapper = null
  var vmState : TadsVMState = null
  
  override def beforeEach {
    objectSystem = new ObjectSystem
    functionSetMapper = new IntrinsicFunctionSetMapper
    vmState = new TadsVMState(objectSystem, functionSetMapper)
  }
  "TadsString" should "be created" in {
    makeString(1, "astring").length should equal ("astring".length)
  }
  it should "be concatenated" in {
    val str1 = makeString(1, "Hello, ")
    val str2 = makeString(2, "World !")
    val str3Id = str1 + str2.id
    objectSystem.toTadsString(str3Id).string should equal ("Hello, World !")
  }
  it should "do a find" in {
    val str1 = makeString(1, "Hello, World !")
    val str2 = makeString(3, "ello")
    val str3 = makeString(2, "salami")

    // success
    str1.find(str1, 1) should equal (1)
    str1.find(str2, 1) should equal (2)
    // failure
    str1.find(str2, 3) should equal (0)
    str1.find(str3, 1) should equal (0)
  }
  it should "do a findReplace" in {
    val str1 = makeString(1, "blablabla")
    ( str1.findReplace(makeString(2, "bla"),
                     makeString(3, "rhabarber"), true, 1).string
     should equal ("rhabarberrhabarberrhabarber") )
    ( str1.findReplace(makeString(2, "bla"),
                       makeString(3, "rhabarber"), false, 2).string
     should equal ("blarhabarberbla") )
    ( str1.findReplace(makeString(2, "bla"),
                       makeString(3, "rhabarber"), true, 2).string
     should equal ("blarhabarberrhabarber") )
  }
  it should "do a substr" in {
    val str1 = makeString(1, "abcdef")
    str1.substr(3).string     should equal ("cdef")
    str1.substr(3, 2).string  should equal ("cd")

    val str2 = makeString(2, "abcdefghi")
    str2.substr(-3).string    should equal ("ghi")
    str2.substr(-3, 2).string should equal ("gh")
    str2.substr(-3, 5).string should equal ("ghi")
    str2.substr(1, 0).string  should equal ("")
  }
  it should "do substr with start = 0 (undocumented)" in {
    makeString(1, "abcdef").substr(0, 1).string should equal ("a")
  }
  it should "when containing invisible char not equal to empty string" in {
    // this is a strange case that happened while
    // developing, we keep it to catch it in the future
    val str1 = makeString(1, "\u000f")
    val str2 = makeString(2, "")
    str1 should not equal (str2)
  }
  it should "perform endsWith()" in {
    val str1 = makeString(1, "HelloWorld")
    val str2 = makeString(2, "World")
    val str3 = makeString(3, "Welt")
      
    str1.endsWith(str2) should be (true)
    str1.endsWith(str3) should be (false)
  }
  it should "perform startsWith()" in {
    val str1 = makeString(1, "HelloWorld")
    val str2 = makeString(2, "Hello")
    val str3 = makeString(3, "Hallo")
      
    str1.startsWith(str2) should be (true)
    str1.startsWith(str3) should be (false)
  }

  private def makeString(id: Int, str: String) = {
    val result = new TadsString(T3ObjectId(id), vmState, false)
    result.init(str)
    objectSystem.registerObject(result)
    result
  }
}
