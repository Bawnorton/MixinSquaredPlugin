package com.bawnorton.msp.util

import com.demonwav.mcdev.platform.mixin.util.MixinConstants

object MixinSquaredConstants {
    const val TARGET_HANDLER = "com.bawnorton.mixinsquared.TargetHandler"
}

val MixinConstants.MixinExtras.MODIFY_EXPRESSION_VALUE: String
    get() = "com.llamalad7.mixinextras.injector.ModifyExpressionValue"
val MixinConstants.MixinExtras.MODIFY_RECEIVER: String
    get() = "com.llamalad7.mixinextras.injector.ModifyReceiver"
val MixinConstants.MixinExtras.MODIFY_RETURN_VALUE: String
    get() = "com.llamalad7.mixinextras.injector.ModifyReturnValue"
val MixinConstants.MixinExtras.WRAP_WITH_CONDITION: String
    get() = "com.llamalad7.mixinextras.injector.WrapWithCondition"
