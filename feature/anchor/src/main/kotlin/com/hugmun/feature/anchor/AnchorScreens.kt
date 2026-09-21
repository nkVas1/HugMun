/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.feature.anchor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable as foundationClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hugmun.core.designsystem.component.HugCard
import com.hugmun.core.designsystem.component.HugEvidenceBadge
import com.hugmun.core.designsystem.component.HugNote
import com.hugmun.core.designsystem.component.HugPrimaryButton
import com.hugmun.core.designsystem.component.HugScreen
import com.hugmun.core.designsystem.component.HugScreenTitle
import com.hugmun.core.designsystem.component.HugSecondaryButton
import com.hugmun.core.designsystem.component.HugSectionHeader
import com.hugmun.core.designsystem.component.HugTextButton
import com.hugmun.core.designsystem.component.NoteTone
import com.hugmun.core.designsystem.theme.HugMunTheme
import com.hugmun.core.model.AnchorItem
import com.hugmun.core.model.AnchorKind
import com.hugmun.core.model.CueLevel
import com.hugmun.core.model.Practice
import kotlinx.coroutines.delay

/**
 * The list of things the user is holding on to.
 *
 * Reads as a small personal notebook rather than a deck of flashcards. The word
 * "карточка" does not appear anywhere: these are a grandson's name and where the keys
 * live, and framing them as study material would be both inaccurate and diminishing.
 */
@Composable
public fun AnchorListScreen(
    viewModel: AnchorViewModel,
    onStartReview: () -> Unit,
    onAdd: () -> Unit,
    onOpenEvidence: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = HugMunTheme.colors

    HugScreen(modifier = modifier) {
        HugScreenTitle(
            text = Practice.ANCHOR.displayName,
            supporting = Practice.ANCHOR.subtitle,
        )

        if (state.items.isEmpty()) {
            HugCard {
                Text(
                    text = "Здесь можно записать то, что важно не забыть: имена внуков, " +
                        "где лежат ключи, когда принимать лекарство. Приложение будет " +
                        "напоминать вам об этом всё реже — ровно настолько, чтобы держалось " +
                        "в памяти.",
                    style = HugMunTheme.type.bodyL,
                    color = colors.ink,
                )
                HugEvidenceBadge(tier = Practice.ANCHOR.tier, onClick = onOpenEvidence)
            }
            HugPrimaryButton(text = "Записать первое", onClick = onAdd)
            return@HugScreen
        }

        if (state.dueCount > 0) {
            HugPrimaryButton(
                text = "Повторить",
                supportingText = "${state.dueCount} на сегодня",
                onClick = onStartReview,
            )
        } else {
            HugNote(
                text = "На сегодня всё повторено. Следующее напоминание придёт, когда " +
                    "подойдёт срок.",
            )
        }

        HugSectionHeader(text = "Что вы храните")

        state.items.forEach { item ->
            AnchorItemCard(item = item, onArchive = { viewModel.archive(item.id) })
        }

        HugSecondaryButton(text = "Записать ещё", onClick = onAdd)
    }
}

@Composable
private fun AnchorItemCard(item: AnchorItem, onArchive: () -> Unit) {
    val colors = HugMunTheme.colors
    var confirming by remember { mutableStateOf(false) }

    HugCard {
        Text(text = item.kind.displayName, style = HugMunTheme.type.label, color = colors.inkFaint)
        Text(text = item.prompt, style = HugMunTheme.type.bodyL, color = colors.ink)
        Text(text = item.answer, style = HugMunTheme.type.titleM, color = colors.dawn)

        item.note?.let {
            Text(text = it, style = HugMunTheme.type.bodyM, color = colors.inkMuted)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (item.isNew) "Ещё не повторяли" else CueLevel.describe(item.cueLevel),
                style = HugMunTheme.type.label,
                color = colors.inkFaint,
            )
            if (confirming) {
                Row(horizontalArrangement = Arrangement.spacedBy(HugMunTheme.dimens.spaceS)) {
                    HugTextButton(text = "Отмена", onClick = { confirming = false })
                    HugTextButton(text = "Убрать", emphasis = true, onClick = onArchive)
                }
            } else {
                HugTextButton(text = "Убрать", onClick = { confirming = true })
            }
        }
    }
}

/** Adding something worth remembering. */
@Composable
public fun AnchorAddScreen(viewModel: AnchorViewModel, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val colors = HugMunTheme.colors
    val dimens = HugMunTheme.dimens

    var kind by remember { mutableStateOf(AnchorKind.PERSON) }
    var prompt by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    HugScreen(modifier = modifier) {
        HugScreenTitle(text = "Что записать")

        HugCard {
            Text(text = "Это про что?", style = HugMunTheme.type.titleM, color = colors.ink)
            AnchorKind.entries.forEach { candidate ->
                KindOption(
                    kind = candidate,
                    selected = candidate == kind,
                    onSelect = { kind = candidate },
                )
            }
        }

        HugCard {
            Text(text = "Вопрос", style = HugMunTheme.type.label, color = colors.inkMuted)
            Text(
                text = "Как вы хотите, чтобы приложение спросило.",
                style = HugMunTheme.type.bodyM,
                color = colors.inkFaint,
            )
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                modifier = Modifier.fillMaxWidth(),
                textStyle = HugMunTheme.type.bodyL,
                placeholder = { Text(kind.example.substringBefore(" — "), style = HugMunTheme.type.bodyM) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                singleLine = false,
            )
        }

        HugCard {
            Text(text = "Ответ", style = HugMunTheme.type.label, color = colors.inkMuted)
            OutlinedTextField(
                value = answer,
                onValueChange = { answer = it },
                modifier = Modifier.fillMaxWidth(),
                textStyle = HugMunTheme.type.bodyL,
                placeholder = { Text(kind.example.substringAfter(" — "), style = HugMunTheme.type.bodyM) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                singleLine = true,
            )
        }

        HugCard {
            Text(text = "Примечание, если нужно", style = HugMunTheme.type.label, color = colors.inkMuted)
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                textStyle = HugMunTheme.type.bodyM,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                singleLine = false,
            )
        }

        HugNote(
            text = "Первый раз приложение просто покажет ответ — проверять сразу оно не " +
                "будет. Так и задумано: ошибку лучше не допускать вовсе, чем потом " +
                "исправлять.",
            tone = NoteTone.Informational,
            modifier = Modifier.padding(top = dimens.spaceS),
        )

        HugPrimaryButton(
            text = "Сохранить",
            onClick = {
                viewModel.addItem(kind, prompt, answer, note)
                onDone()
            },
            enabled = prompt.isNotBlank() && answer.isNotBlank(),
        )
    }
}

@Composable
private fun KindOption(kind: AnchorKind, selected: Boolean, onSelect: () -> Unit) {
    val colors = HugMunTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(HugMunTheme.shapes.input)
            .background(if (selected) colors.dawnWash else colors.surfaceBase)
            .border(
                width = if (selected) 3.dp else 1.dp,
                brush = SolidColor(if (selected) colors.dawn else colors.controlEdge),
                shape = HugMunTheme.shapes.input,
            )
            .foundationClickable(role = Role.RadioButton, onClick = onSelect)
            .padding(HugMunTheme.dimens.spaceM),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HugMunTheme.dimens.spaceM),
    ) {
        Box(
            modifier = Modifier
                .padding(2.dp)
                .clip(HugMunTheme.shapes.chip)
                .background(if (selected) colors.dawn else colors.surfaceEdge)
                .padding(7.dp),
        )
        Column {
            Text(text = kind.displayName, style = HugMunTheme.type.titleM, color = colors.ink)
            Text(text = kind.example, style = HugMunTheme.type.bodyM, color = colors.inkMuted)
        }
    }
}

/**
 * The review session.
 *
 * Errorless learning made literal. At the fully-cued level the correct option carries a
 * visible mark, so a wrong answer cannot be produced. At every level a six-second pause
 * reveals the answer before frustration can turn into an error. The word "неверно"
 * never appears; the worst outcome the screen can show is "подсказали".
 */
@Composable
public fun AnchorReviewScreen(viewModel: AnchorViewModel, onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = HugMunTheme.colors
    val dimens = HugMunTheme.dimens

    LaunchedEffect(Unit) { viewModel.startReview() }

    // The reveal timer. It is the mechanism that prevents an error, so it runs on every
    // prompt rather than only when the user looks stuck.
    LaunchedEffect(state.prompt?.item?.id) {
        val prompt = state.prompt ?: return@LaunchedEffect
        if (prompt.isFullyCued) return@LaunchedEffect
        delay(AnchorViewModel.REVEAL_AFTER_MILLIS)
        viewModel.reveal()
    }

    val prompt = state.prompt

    if (state.isSessionFinished || prompt == null) {
        HugScreen(modifier = modifier) {
            HugScreenTitle(text = "Повторение закончено")
            HugCard {
                Text(
                    text = "Повторено: ${state.reviewedInSession}.",
                    style = HugMunTheme.type.bodyL,
                    color = colors.ink,
                )
                if (state.helpedInSession > 0) {
                    Text(
                        text = "Из них с подсказкой: ${state.helpedInSession}. Это нормально — " +
                            "подсказка здесь часть метода, а не оценка.",
                        style = HugMunTheme.type.bodyM,
                        color = colors.inkMuted,
                    )
                }
            }
            HugPrimaryButton(text = "Готово", onClick = onFinished)
        }
        return
    }

    HugScreen(modifier = modifier) {
        Text(
            text = "${state.reviewedInSession + 1} из ${state.reviewedInSession + 1 + state.dueCount}",
            style = HugMunTheme.type.label,
            color = colors.inkFaint,
        )

        HugCard {
            Text(text = prompt.item.kind.displayName, style = HugMunTheme.type.label, color = colors.inkFaint)
            Text(text = prompt.item.prompt, style = HugMunTheme.type.displayM, color = colors.ink)
            prompt.hint?.let {
                Text(
                    text = it,
                    style = HugMunTheme.type.bodyM,
                    color = colors.inkMuted,
                    modifier = Modifier.padding(top = dimens.spaceXs),
                )
            }
        }

        val revealed = state.revealedAnswer
        if (revealed != null) {
            HugCard {
                Text(text = "Ответ", style = HugMunTheme.type.label, color = colors.inkMuted)
                Text(text = revealed, style = HugMunTheme.type.displayM, color = colors.dawn)
                prompt.item.note?.let {
                    Text(text = it, style = HugMunTheme.type.bodyM, color = colors.inkMuted)
                }
            }
            HugPrimaryButton(text = "Дальше", onClick = viewModel::continueAfterReveal)
        } else {
            prompt.options.forEach { option ->
                AnswerOption(
                    text = option,
                    // At the fully-cued level the correct option is marked, which is what
                    // makes an error impossible rather than merely unlikely.
                    highlighted = prompt.isFullyCued && option == prompt.item.answer,
                    onClick = { viewModel.choose(option) },
                )
            }
            HugTextButton(text = "Показать ответ", onClick = viewModel::reveal)
        }
    }
}

@Composable
private fun AnswerOption(text: String, highlighted: Boolean, onClick: () -> Unit) {
    val colors = HugMunTheme.colors

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(HugMunTheme.shapes.primaryAction)
            .background(if (highlighted) colors.dawnWash else colors.surfaceRaised)
            .border(
                width = if (highlighted) 3.dp else 2.dp,
                brush = SolidColor(if (highlighted) colors.dawn else colors.controlEdge),
                shape = HugMunTheme.shapes.primaryAction,
            )
            .foundationClickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 20.dp, horizontal = HugMunTheme.dimens.spaceL),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = HugMunTheme.type.titleM,
            color = colors.ink,
            textAlign = TextAlign.Center,
        )
    }
}
