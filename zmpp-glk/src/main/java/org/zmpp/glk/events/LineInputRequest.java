/*
 * Created on 2012/02/21
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
package org.zmpp.glk.events;

import org.zmpp.glk.*;
import org.zmpp.glk.windows.*;

public final class LineInputRequest extends WindowEventRequest {
    public int buffer;
    public int maxlen;
    public int initlen;
    public boolean useUnicode;
    private boolean runOnce;

    public LineInputRequest(int winId, int buffer, int maxlen,
                            int initlen, boolean useUnicode) {
        super(winId, GlkEventType.LineInput);
        this.buffer     = buffer;
        this.maxlen     = maxlen;
        this.initlen    = initlen;
        this.useUnicode = useUnicode;
    }

    @Override public boolean equals(Object that) {
        if (that instanceof LineInputRequest) {
            return winId == ((LineInputRequest) that).winId;
        } else {
            return false;
        }
    }

    public void prepareWindow(GlkScreenUI screenUI) {
        // Line input requests can interfere with timed input interrupts.
        // We need to save the mark until the program prints out input
        if (runOnce) {
            screenUI.requestPreviousLineInput(winId);
        } else {
            screenUI.requestLineInput(winId);
            runOnce = true;
        }
    }

    @Override public int hashCode() { return winId; }
}
