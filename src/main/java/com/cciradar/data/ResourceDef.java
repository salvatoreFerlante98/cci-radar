package com.cciradar.data;

import java.util.List;

public record ResourceDef(
        String resourceKey,
        String label,
        int color,
        String iconPath,
        List<String> coeVeinRecipeIds
) {}
