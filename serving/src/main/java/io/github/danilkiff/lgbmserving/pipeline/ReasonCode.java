package io.github.danilkiff.lgbmserving.pipeline;

/**
 * Один ранжированный contribution в решение: признак, его код и метка
 * adverse-action, само значение SHAP. В объяснение отбираются только толкавшие
 * к отклонению, поэтому contribution всегда положителен.
 */
public record ReasonCode(int feature, String code, String label, double contribution) {}
