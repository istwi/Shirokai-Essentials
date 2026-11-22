package dev.zorykai.cosmetics.client;

/** Fixed phase rate avoids age*speed jumps. Amplitude eases over eight ticks. */
public final class WingMotion {
    private double lastTime=Double.NaN,phase;
    private float amount;
    public float update(double time,float movement) {
        if(!Double.isFinite(time)||!Float.isFinite(movement))return 0;
        if(Double.isNaN(lastTime)||time<lastTime){lastTime=time;phase=0;amount=0;}
        double elapsed=Math.min(2,Math.max(0,time-lastTime));lastTime=time;
        float target=Math.max(0,Math.min(1,(movement-.01f)*4));
        amount+=(target-amount)*(float)(1-Math.exp(-elapsed/8));
        phase=(phase+elapsed*Math.PI*2/70)%(Math.PI*2);
        return amount*(.36f+.32f*(float)Math.sin(phase));
    }
}
