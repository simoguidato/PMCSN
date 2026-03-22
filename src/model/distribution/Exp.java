package model.distribution;

import libs.Rngs;

public class Exp implements Distribution {
    private Rngs rngs;
    private int streamIndex;

    public Exp(Rngs rngs, int streamIndex) {
        this.rngs = rngs;
        this.streamIndex = streamIndex;
    }

    @Override
    public double generate(double mean) {
        rngs.selectStream(streamIndex);
        return -mean * Math.log(rngs.random());
    }
}
