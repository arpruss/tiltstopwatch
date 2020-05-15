package omegacentauri.mobi.tiltstopwatch;

interface MyTimeKeeper {
    void updateViews();
    void restore();
    void stopUpdating();
    void destroy();
    void suspend();
}
