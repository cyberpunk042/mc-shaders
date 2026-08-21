/*
 * Ported from the-virus-block-mc (net.cyberpunk042.visual), where it is
 * geometry model code with no Minecraft or graphics-API dependency.
 * Relicensed to MIT here by the author, per the engine/content split
 * recorded in docs/PORTING.md.
 *
 * JSON binding was removed on the way across: the model stays free of
 * serialisation so it can be loaded from external content. The @JsonField
 * metadata is retained for a codec layer above core.
 */
package net.cyberpunk042.mcshaders.core.animation;


import java.util.*;

/**
 * Orchestrates multi-stage lifecycle animations for primitives.
 * 
 * <p>A LifecycleAnimator manages a sequence of {@link StageConfig} stages,
 * tracking current stage, elapsed time, and providing computed modifiers.</p>
 * 
 * <h2>Stage Flow</h2>
 * <pre>
 * Stage 0     Stage 1     Stage 2     Stage 3
 * [CHARGE] → [FIRE] → [SUSTAIN] → [DESPAWN]
 *    60t       15t      indefinite     30t
 * </pre>
 * 
 * <h2>Usage</h2>
 * <pre>
 * LifecycleAnimator animator = LifecycleAnimator.builder()
 *     .id("kamehameha_orb")
 *     .addStage(StageConfig.CHARGE)
 *     .addStage(StageConfig.FIRE.toBuilder().duration(10).build())
 *     .addStage(StageConfig.SUSTAIN)
 *     .addStage(StageConfig.DESPAWN)
 *     .build();
 *     
 * // Start lifecycle
 * animator.start();
 * 
 * // Each frame, get current modifiers
 * float scale = animator.getCurrentScale();
 * float alpha = animator.getCurrentAlpha();
 * StageConfig stage = animator.getCurrentStage();
 * </pre>
 * 
 * @see StageConfig
 * @see ShapeModifier
 */
public class LifecycleAnimator {
    
    private final String id;
    private final List<StageConfig> stages;
    private final boolean loop;
    private final Map<String, Integer> stageIndexById;
    
    // Runtime state
    private int currentStageIndex = 0;
    private long stageStartTick = 0;
    private long totalElapsedTicks = 0;
    private boolean started = false;
    private boolean complete = false;
    private boolean paused = false;
    
    // =========================================================================
    // Construction
    // =========================================================================
    
    private LifecycleAnimator(String id, List<StageConfig> stages, boolean loop) {
        this.id = id;
        this.stages = new ArrayList<>(stages);
        this.loop = loop;
        
        // Build index for stage lookup by ID
        this.stageIndexById = new HashMap<>();
        for (int i = 0; i < stages.size(); i++) {
            stageIndexById.put(stages.get(i).id(), i);
        }
    }
    
    // =========================================================================
    // Lifecycle Control
    // =========================================================================
    
    /**
     * Start the lifecycle from the first stage.
     */
    public void start() {
        this.currentStageIndex = 0;
        this.stageStartTick = 0;
        this.totalElapsedTicks = 0;
        this.started = true;
        this.complete = false;
        this.paused = false;

    }
    
    /**
     * Advance to a specific stage by ID.
     */
    public void goToStage(String stageId) {
        Integer index = stageIndexById.get(stageId);
        if (index != null) {
            goToStageIndex(index);
        } else {

        }
    }
    
    /**
     * Advance to a specific stage by index.
     */
    public void goToStageIndex(int index) {
        if (index >= 0 && index < stages.size()) {
            int oldIndex = currentStageIndex;
            currentStageIndex = index;
            stageStartTick = totalElapsedTicks;
            complete = false;

        }
    }
    
    /**
     * Advance to the next stage.
     * @return true if advanced, false if already at last stage (and not looping)
     */
    public boolean nextStage() {
        StageConfig current = getCurrentStage();
        
        // Check for explicit next stage
        if (current.nextStageId() != null) {
            Integer nextIndex = stageIndexById.get(current.nextStageId());
            if (nextIndex != null) {
                goToStageIndex(nextIndex);
                return true;
            }
        }
        
        // Sequential advance
        if (currentStageIndex < stages.size() - 1) {
            goToStageIndex(currentStageIndex + 1);
            return true;
        } else if (loop) {
            goToStageIndex(0);
            return true;
        } else {
            complete = true;

            return false;
        }
    }
    
    /**
     * Manually trigger transition (for MANUAL stages).
     */
    public void triggerTransition() {
        if (getCurrentStage().transition() == StageTransition.MANUAL) {
            nextStage();
        }
    }
    
    /**
     * Tick the animator (call each frame).
     */
    public void tick() {
        if (!started || complete || paused) return;
        
        totalElapsedTicks++;
        
        StageConfig current = getCurrentStage();
        long stageElapsed = getStageElapsedTicks();
        
        // Check for automatic transition
        if (current.transition() == StageTransition.TIME && current.isComplete(stageElapsed)) {
            nextStage();
        } else if (current.transition() == StageTransition.CHAIN) {
            nextStage();
        }
    }
    
    /**
     * Pause the animator.
     */
    public void pause() {
        this.paused = true;
    }
    
    /**
     * Resume the animator.
     */
    public void resume() {
        this.paused = false;
    }
    
    /**
     * Reset to initial state.
     */
    public void reset() {
        this.currentStageIndex = 0;
        this.stageStartTick = 0;
        this.totalElapsedTicks = 0;
        this.started = false;
        this.complete = false;
        this.paused = false;
    }
    
    // =========================================================================
    // State Queries
    // =========================================================================
    
    public String id() { return id; }
    
    public boolean isStarted() { return started; }
    
    public boolean isComplete() { return complete; }
    
    public boolean isPaused() { return paused; }
    
    public boolean isActive() { return started && !complete && !paused; }
    
    public int getCurrentStageIndex() { return currentStageIndex; }
    
    public StageConfig getCurrentStage() {
        if (stages.isEmpty()) return StageConfig.ACTIVE;
        return stages.get(Math.min(currentStageIndex, stages.size() - 1));
    }
    
    public StageConfig getStage(String id) {
        Integer idx = stageIndexById.get(id);
        return idx != null ? stages.get(idx) : null;
    }
    
    public List<StageConfig> getStages() {
        return Collections.unmodifiableList(stages);
    }
    
    public int getStageCount() { return stages.size(); }
    
    public long getTotalElapsedTicks() { return totalElapsedTicks; }
    
    public long getStageElapsedTicks() { return totalElapsedTicks - stageStartTick; }
    
    /**
     * Get progress through current stage (0-1).
     */
    public float getStageProgress() {
        return getCurrentStage().progress(getStageElapsedTicks());
    }
    
    /**
     * Get overall lifecycle progress (0-1).
     */
    public float getLifecycleProgress() {
        if (stages.isEmpty()) return 1f;
        
        long totalDuration = 0;
        long elapsed = 0;
        
        for (int i = 0; i < stages.size(); i++) {
            int duration = stages.get(i).duration();
            if (duration <= 0) duration = 100; // Estimate for indefinite stages
            
            if (i < currentStageIndex) {
                elapsed += duration;
            } else if (i == currentStageIndex) {
                elapsed += Math.min(getStageElapsedTicks(), duration);
            }
            totalDuration += duration;
        }
        
        return totalDuration > 0 ? (float) elapsed / totalDuration : 1f;
    }
    
    // =========================================================================
    // Modifier Queries (for rendering)
    // =========================================================================
    
    /**
     * Get current scale modifier based on stage and progress.
     */
    public float getCurrentScale() {
        return getCurrentStage().effectiveShapeModifier().computeScale(getStageProgress());
    }
    
    /**
     * Get current length modifier based on stage and progress.
     */
    public float getCurrentLength() {
        return getCurrentStage().effectiveShapeModifier().computeLength(getStageProgress());
    }
    
    /**
     * Get current radius modifier based on stage and progress.
     */
    public float getCurrentRadius() {
        return getCurrentStage().effectiveShapeModifier().computeRadius(getStageProgress());
    }
    
    /**
     * Get current alpha modifier based on stage and progress.
     */
    public float getCurrentAlpha() {
        return getCurrentStage().effectiveShapeModifier().computeAlpha(getStageProgress());
    }
    
    /**
     * Whether primitive should be visible in current stage.
     */
    public boolean isVisible() {
        return getCurrentStage().visible();
    }
    
    /**
     * Get the stage-specific animation override, if any.
     */
    public Animation getAnimationOverride() {
        return getCurrentStage().animationOverride();
    }
    
    // =========================================================================
    // Builder & Serialization
    // =========================================================================
    
    public static Builder builder() { return new Builder(); }
    
    public Builder toBuilder() {
        Builder b = new Builder().id(id).loop(loop);
        for (StageConfig stage : stages) {
            b.addStage(stage);
        }
        return b;
    }
    
    
    
    
    
    public static class Builder {
        private String id = "lifecycle";
        private final List<StageConfig> stages = new ArrayList<>();
        private boolean loop = false;
        
        public Builder id(String id) { this.id = id; return this; }
        public Builder loop(boolean loop) { this.loop = loop; return this; }
        
        public Builder addStage(StageConfig stage) {
            if (stage != null) stages.add(stage);
            return this;
        }
        
        public Builder addStages(StageConfig... stages) {
            for (StageConfig stage : stages) {
                addStage(stage);
            }
            return this;
        }
        
        public Builder addStages(List<StageConfig> stages) {
            for (StageConfig stage : stages) {
                addStage(stage);
            }
            return this;
        }
        
        public Builder clearStages() {
            stages.clear();
            return this;
        }
        
        public LifecycleAnimator build() {
            // Add default active stage if empty
            if (stages.isEmpty()) {
                stages.add(StageConfig.ACTIVE);
            }
            return new LifecycleAnimator(id, stages, loop);
        }
    }
    
    // =========================================================================
    // Presets
    // =========================================================================
    
    /**
     * Simple spawn → active → despawn lifecycle.
     */
    public static LifecycleAnimator simple(String id) {
        return builder()
            .id(id)
            .addStage(StageConfig.SPAWN)
            .addStage(StageConfig.ACTIVE)
            .addStage(StageConfig.DESPAWN)
            .build();
    }
    
    /**
     * Kamehameha orb lifecycle.
     */
    public static LifecycleAnimator kamehamehaOrb() {
        return builder()
            .id("kamehameha_orb")
            .addStage(StageConfig.CHARGE)
            .addStage(StageConfig.builder()
                .id("fire")
                .duration(10)
                .transition(StageTransition.TIME)
                .shapeModifier(ShapeModifier.builder()
                    .scale(1f, 0.85f, EaseFunction.EASE_OUT_QUAD)
                    .build())
                .build())
            .addStage(StageConfig.SUSTAIN)
            .addStage(StageConfig.builder()
                .id("despawn")
                .duration(30)
                .transition(StageTransition.TIME)
                .shapeModifier(ShapeModifier.builder()
                    .scale(0.85f, 0f, EaseFunction.EASE_IN_QUAD)
                    .alpha(1f, 0f, EaseFunction.EASE_IN_QUAD)
                    .build())
                .build())
            .build();
    }
    
    /**
     * Kamehameha beam lifecycle.
     */
    public static LifecycleAnimator kamehamehaBeam() {
        return builder()
            .id("kamehameha_beam")
            .addStage(StageConfig.builder()
                .id("hidden")
                .visible(false)
                .transition(StageTransition.MANUAL)
                .build())
            .addStage(StageConfig.builder()
                .id("fire")
                .duration(15)
                .transition(StageTransition.TIME)
                .shapeModifier(ShapeModifier.builder()
                    .length(0f, 1f, EaseFunction.EASE_OUT_EXPO)
                    .radius(0.3f, 1f, EaseFunction.EASE_OUT_QUAD)
                    .build())
                .build())
            .addStage(StageConfig.SUSTAIN)
            .addStage(StageConfig.builder()
                .id("retract")
                .duration(20)
                .transition(StageTransition.TIME)
                .shapeModifier(ShapeModifier.builder()
                    .length(1f, 0f, EaseFunction.EASE_IN_QUAD)
                    .alpha(1f, 0f, EaseFunction.EASE_IN_QUAD)
                    .build())
                .build())
            .build();
    }
}
