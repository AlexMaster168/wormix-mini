package com.riftartillery.game

import java.util.Locale

internal class FrameStats {
    private val samples=FloatArray(5000)
    private var count=0
    private var cursor=0
    @Synchronized fun record(nanos:Long) {
        if(nanos<=0) return
        samples[cursor]=nanos/1e6f;cursor=(cursor+1)%samples.size;count=minOf(count+1,samples.size)
    }
    @Synchronized fun report():String {
        if(count==0) return "FRAME_COUNT=0"
        val sorted=samples.copyOf(count).sorted()
        val p95=sorted[((count-1)*.95).toInt()]
        val jank=sorted.count {it>16.667f}*100f/count
        return String.format(Locale.US,"FRAME_COUNT=%d PIPELINE_P95_MS=%.2f PIPELINE_OVER_16_7_PERCENT=%.2f",count,p95,jank)
    }
}
