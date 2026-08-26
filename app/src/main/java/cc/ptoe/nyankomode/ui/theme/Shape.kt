package cc.ptoe.nyankomode.ui.theme

import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Shapes

/**
 * 官方 Expressive 角刻度（material3 1.5.0-alpha26 Shapes）：
 * medium=12 / large=16 / largeIncreased=20 / extraLarge=28 /
 * extraLargeIncreased=32 / extraExtraLarge=48，full=CircleShape。
 */
val ExpressiveShapes: Shapes = Shapes(
    extraSmall = ShapeDefaults.ExtraSmall,
    small = ShapeDefaults.Small,
    medium = ShapeDefaults.Medium,
    // 取更大的增量刻度，营造 Expressive 张力
    large = ShapeDefaults.LargeIncreased,
    largeIncreased = ShapeDefaults.ExtraLargeIncreased,
    extraLarge = ShapeDefaults.ExtraLargeIncreased,
    extraLargeIncreased = ShapeDefaults.ExtraExtraLarge,
    extraExtraLarge = ShapeDefaults.ExtraExtraLarge,
)
