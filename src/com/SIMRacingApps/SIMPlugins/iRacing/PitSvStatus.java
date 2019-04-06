package com.SIMRacingApps.SIMPlugins.iRacing;

//enum irsdk_PitSvStatus
/**
 * Copyright (c) 2013, iRacing.com Motorsport Simulations, LLC.
 * All rights reserved.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * @author Jeffrey Gilliam
 * @copyright Copyright (C) 2019 - 2019 Jeffrey Gilliam
 * @since 1.9
 * @license Apache License 2.0
 */

/*This was released by iRacing in the March 2019 build in the 2nd patch */
public class PitSvStatus {
    public final static int None           = 0;
    public final static int InProgress     = 1;
    public final static int Complete       = 2;
    public final static int TooFarLeft     = 100;
    public final static int TooFarRight    = 101;
    public final static int TooFarForward  = 102;
    public final static int TooFarBack     = 103;
    public final static int BadAngle       = 104;
    public final static int CantFixThat    = 105;
}
