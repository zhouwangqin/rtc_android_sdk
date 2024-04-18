/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.webrtc;

/**
 * Java version of webrtc::AudioSinkInterface.
 */
public interface AudioSink {
  // audio data call back, zhouwq add
  @CalledByNative void onFrame(byte[] frame, int bits_per_sample, int sample_rate, int number_of_channels, int number_of_frames, double audioLevel);
}