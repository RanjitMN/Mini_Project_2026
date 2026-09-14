public enum Priority {
    CRITICAL(3),
    ESSENTIAL(2),
    DEFERRABLE(1);

    private final int level;

    Priority(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }
}
