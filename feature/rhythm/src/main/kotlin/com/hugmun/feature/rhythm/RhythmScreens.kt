/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.feature.rhythm

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable as foundationSelectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hugmun.core.designsystem.component.HugCard
import com.hugmun.core.designsystem.component.HugEvidenceBadge
import com.hugmun.core.designsystem.component.HugNote
import com.hugmun.core.designsystem.component.HugPrimaryButton
import com.hugmun.core.designsystem.component.HugRule
import com.hugmun.core.designsystem.component.HugScreen
import com.hugmun.core.designsystem.component.HugScreenTitle
import com.hugmun.core.designsystem.component.HugSecondaryButton
import com.hugmun.core.designsystem.component.NoteTone
import com.hugmun.core.designsystem.theme.HugMunTheme
import com.hugmun.core.model.Practice
import com.hugmun.engine.visuals.PhoticAvailability
import com.hugmun.engine.visuals.PhoticBlockReason

/**
 * The entry point for «Ритм».
 *
 * Deliberately front-loads the caveats. This is the one practice in the app with tier C
 * evidence and a real, if small, physical risk, and the screen is arranged so that a
 * user who reads only the first card has already read the honest version.
 */
@Composable
public fun RhythmIntroScreen(
    viewModel: RhythmViewModel,
    onStartAudioOnly: () -> Unit,
    onStartWithLight: () -> Unit,
    onOpenScreening: () -> Unit,
    onOpenEvidence: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = HugMunTheme.colors

    HugScreen(modifier = modifier) {
        HugScreenTitle(
            text = Practice.RHYTHM.displayName,
            supporting = Practice.RHYTHM.subtitle,
        )

        HugNote(
            text = "Это эксперимент, а не лечение. Исследования на людях пока очень " +
                "небольшие, и говорить о действии рано. Мы включили это, потому что " +
                "направление интересное, и честно говорим, чего про него не знаем.",
            tone = NoteTone.Caution,
        )

        HugEvidenceBadge(tier = Practice.RHYTHM.tier, onClick = onOpenEvidence)

        HugCard {
            Text(text = "Звук", style = HugMunTheme.type.titleM, color = colors.ink)
            Text(
                text = "Мягкий шум, который пульсирует сорок раз в секунду. Именно такой " +
                    "звук использовали в исследованиях — не «бинауральные ритмы», которые " +
                    "продают под тем же названием и которые работают иначе.",
                style = HugMunTheme.type.bodyL,
                color = colors.ink,
            )
            Text(
                text = "Слушать лучше негромко. Если носите слуховой аппарат — сначала " +
                    "спросите своего специалиста.",
                style = HugMunTheme.type.bodyM,
                color = colors.inkMuted,
            )
        }

        LightCard(
            availability = state.photic,
            onOpenScreening = onOpenScreening,
        )

        HugPrimaryButton(
            text = "Только звук",
            supportingText = "безопасно, без мерцания",
            onClick = onStartAudioOnly,
            enabled = state.isAudioAvailable,
        )

        if (state.photic.isAvailable) {
            HugSecondaryButton(text = "Звук и свет", onClick = onStartWithLight)
        }
    }
}

@Composable
private fun LightCard(availability: PhoticAvailability, onOpenScreening: () -> Unit) {
    val colors = HugMunTheme.colors

    HugCard {
        Text(text = "Свет", style = HugMunTheme.type.titleM, color = colors.ink)

        when (availability) {
            PhoticAvailability.Available -> {
                Text(
                    text = "Мягкое янтарное пятно в центре экрана, которое плавно пульсирует. " +
                        "Яркость нарастает постепенно, первые двадцать секунд — почти " +
                        "незаметно. Любое касание экрана мгновенно всё останавливает.",
                    style = HugMunTheme.type.bodyL,
                    color = colors.ink,
                )
            }

            is PhoticAvailability.Blocked -> {
                Text(
                    text = explain(availability.reason),
                    style = HugMunTheme.type.bodyL,
                    color = colors.ink,
                )
                if (availability.reason == PhoticBlockReason.NOT_SCREENED ||
                    availability.reason == PhoticBlockReason.NOT_CONSENTED
                ) {
                    HugSecondaryButton(text = "Пройти анкету", onClick = onOpenScreening)
                }
            }
        }
    }
}

/**
 * Why the light is unavailable, in plain Russian.
 *
 * Never a greyed-out control with no explanation: for this user an unexplained disabled
 * button reads as "I did something wrong", which is both untrue and corrosive.
 */
private fun explain(reason: PhoticBlockReason): String = when (reason) {
    PhoticBlockReason.NOT_SCREENED ->
        "Прежде чем включать свет, нужно ответить на пять вопросов о здоровье. " +
            "Это займёт минуту."

    PhoticBlockReason.NOT_CONSENTED ->
        "Осталось подтвердить согласие: мерцающий свет — отдельный риск, и мы просим " +
            "отметить его отдельно, а не заодно со всем остальным."

    PhoticBlockReason.DISPLAY_CANNOT_RENDER ->
        "Экран этого телефона не может показать сорок вспышек в секунду ровно. " +
            "Приблизительное мерцание было бы одновременно бесполезным и менее " +
            "безопасным, поэтому световую часть мы просто не включаем. Звук работает " +
            "полностью и не зависит от экрана."

    PhoticBlockReason.DAILY_LIMIT_REACHED ->
        "На сегодня уже час со светом. Это предел, который мы себе назначили."

    PhoticBlockReason.LOCKED_AFTER_ADVERSE_EVENT ->
        "Вы отметили, что после занятия вам стало нехорошо. Свет отключён. Прежде чем " +
            "включать его снова, поговорите с врачом."

    PhoticBlockReason.ROOM_TOO_DARK ->
        "В комнате слишком темно. В темноте контраст между вспышками максимальный, " +
            "поэтому включите свет в комнате и попробуйте снова."
}

/**
 * The screening questionnaire.
 *
 * Every question is a contraindication and every "да" is disqualifying. There is no
 * score and no borderline case, which is stated on the screen so the user understands
 * that answering honestly is not being penalised.
 */
@Composable
public fun PhotosensitivityScreeningScreen(
    viewModel: RhythmViewModel,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HugMunTheme.colors
    val dimens = HugMunTheme.dimens
    val answers = remember {
        mutableStateListOf<Boolean?>().apply {
            repeat(PhotosensitivityScreening.QUESTIONS.size) { add(null) }
        }
    }
    val complete = answers.all { it != null }

    HugScreen(modifier = modifier) {
        HugScreenTitle(
            text = "Несколько вопросов о здоровье",
            supporting = "Только про мерцающий свет. Звуковая часть от этого не зависит.",
        )

        HugNote(
            text = "Если хотя бы на один вопрос ответ «да», световую часть мы включать не " +
                "будем. Это не запрет и не оценка — просто у мерцающего света есть " +
                "известный риск, и рисковать тут незачем.",
            tone = NoteTone.Caution,
        )

        PhotosensitivityScreening.QUESTIONS.forEachIndexed { index, question ->
            HugCard {
                Text(text = question, style = HugMunTheme.type.bodyL, color = colors.ink)
                HugRule(modifier = Modifier.padding(vertical = dimens.spaceS))
                Row(horizontalArrangement = Arrangement.spacedBy(dimens.touchTargetGap)) {
                    AnswerChip(
                        label = "Да",
                        selected = answers[index] == true,
                        modifier = Modifier.weight(1f),
                    ) { answers[index] = true }
                    AnswerChip(
                        label = "Нет",
                        selected = answers[index] == false,
                        modifier = Modifier.weight(1f),
                    ) { answers[index] = false }
                }
            }
        }

        HugPrimaryButton(
            text = "Готово",
            onClick = {
                viewModel.submitScreening(answers.map { it == true })
                onFinished()
            },
            enabled = complete,
        )
    }
}

@Composable
private fun AnswerChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onSelect: () -> Unit) {
    val colors = HugMunTheme.colors

    Box(
        modifier = modifier
            .clip(HugMunTheme.shapes.primaryAction)
            .background(if (selected) colors.dawnWash else colors.surfaceBase)
            .border(
                width = if (selected) 3.dp else 2.dp,
                brush = SolidColor(if (selected) colors.dawn else colors.controlEdge),
                shape = HugMunTheme.shapes.primaryAction,
            )
            .foundationSelectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(HugMunTheme.dimens.spaceS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // A filled dot as well as the colour, so selection is never carried by
            // colour alone.
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(HugMunTheme.shapes.chip)
                    .background(if (selected) colors.dawn else colors.surfaceEdge),
            )
            Text(text = label, style = HugMunTheme.type.titleM, color = colors.ink)
        }
    }
}

/**
 * The consent screen.
 *
 * Separate from onboarding consent on purpose. Bundling a photic-stimulation consent
 * into a general "I agree" is how consent becomes meaningless.
 */
@Composable
public fun PhoticConsentScreen(
    viewModel: RhythmViewModel,
    onGranted: () -> Unit,
    onDeclined: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HugMunTheme.colors

    HugScreen(modifier = modifier) {
        HugScreenTitle(text = "Про мерцающий свет")

        HugCard {
            Text(
                text = "Что важно знать",
                style = HugMunTheme.type.titleM,
                color = colors.ink,
            )
            Column(verticalArrangement = Arrangement.spacedBy(HugMunTheme.dimens.spaceM)) {
                Bullet(
                    "Свет будет мерцать сорок раз в секунду. Это намного чаще, чем " +
                        "допускают обычные правила для экранов, — и мы не делаем вид, что это не так.",
                )
                Bullet(
                    "У небольшой части людей мерцающий свет может вызвать приступ. " +
                        "Анкета отсеивает известные факторы риска, но полной гарантии не даёт.",
                )
                Bullet(
                    "Яркость нарастает двадцать секунд, начиная с нуля. Пятно небольшое, " +
                        "в центре экрана, тёплого янтарного цвета.",
                )
                Bullet("Любое касание экрана останавливает всё мгновенно.")
                Bullet(
                    "Если станет нехорошо, закружится голова, заболит голова или " +
                        "появятся странные ощущения — прекратите и скажите врачу.",
                )
            }
        }

        HugNote(
            text = "Согласие можно отозвать в любой момент одним нажатием в настройках. " +
                "Звуковая часть занятия от этого согласия не зависит и остаётся доступной.",
        )

        HugPrimaryButton(
            text = "Согласен, включить свет",
            onClick = {
                viewModel.grantConsent()
                onGranted()
            },
        )
        HugSecondaryButton(text = "Только звук", onClick = onDeclined)
    }
}

@Composable
private fun Bullet(text: String) {
    val colors = HugMunTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(HugMunTheme.dimens.spaceM)) {
        Box(
            modifier = Modifier
                .padding(top = 10.dp)
                .size(6.dp)
                .clip(HugMunTheme.shapes.chip)
                .background(colors.dawn),
        )
        Text(text = text, style = HugMunTheme.type.bodyL, color = colors.ink)
    }
}
