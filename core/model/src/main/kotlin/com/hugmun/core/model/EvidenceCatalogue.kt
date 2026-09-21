/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.core.model

/**
 * The evidence card for every practice in the app.
 *
 * This file is where the research dossier becomes something the user can read. It is
 * the enforcement point for the project's central rule: **no practice ships without an
 * evidence card**, and the card says what was actually found, in whom, and what the
 * limitation is.
 *
 * Every entry here has a counterpart in `docs/research/01-evidence-base.md` and every
 * citation key matches `docs/research/references.bib`. If you change a claim here,
 * change it there first.
 *
 * Voice rules, from `docs/design/design-language.md` §8 and enforced by review:
 * a summary describes **what happened to the participants of a study**, never what will
 * happen to the person reading it.
 */
public object EvidenceCatalogue {

    public fun cardFor(practice: Practice): EvidenceCard = when (practice) {
        Practice.VIGILANCE -> vigilance
        Practice.ANCHOR -> anchor
        Practice.BREATHING -> breathing
        Practice.MOVEMENT -> movement
        Practice.RHYTHM -> rhythm
        Practice.ASSESSMENT -> assessment
    }

    public val all: List<Pair<Practice, EvidenceCard>>
        get() = Practice.entries.map { it to cardFor(it) }

    // -----------------------------------------------------------------------------

    private val vigilance = EvidenceCard(
        tier = EvidenceTier.A,
        headline = "Тренировка скорости обработки — единственное упражнение здесь, " +
            "за которым стоит наблюдение длиной в двадцать лет.",
        whatWasStudied = "Упражнение на скорость обработки зрительной информации: в центре " +
            "экрана на очень короткое время появляется объект, одновременно на периферии — " +
            "метка. Нужно узнать оба. Время показа постепенно сокращается.",
        inWhom = "Около 2800 пожилых людей без нарушений памяти на момент начала. " +
            "Исследование ACTIVE, США. Десять занятий за пять–шесть недель, затем " +
            "повторные курсы примерно через год и через три года.",
        whatWasFound = "Через двадцать лет у тех, кто прошёл основной курс и хотя бы один " +
            "повторный, диагноз деменции ставили на четверть реже, чем в группе сравнения " +
            "(40 % против 49 %). У тех, кто прошёл только основной курс без повторных, " +
            "разницы не было вовсе. Тренировка памяти и тренировка рассуждения значимого " +
            "эффекта не дали.",
        caveat = "Это не гарантия для конкретного человека, а разница между группами. " +
            "Деменция не была главной целью исследования, и диагнозы здесь брали из " +
            "страховых данных, а не ставили специально. Главный вывод для нас другой и он " +
            "надёжнее: повторные курсы обязательны. Именно поэтому приложение будет " +
            "возвращать вас к этому занятию через месяцы и годы.",
        citations = listOf(
            Citation(
                key = "coe2026active",
                authors = "Coe N. B. и соавт.",
                year = 2026,
                title = "Impact of cognitive training on claims-based diagnosed dementia over 20 years",
                journal = "Alzheimer's & Dementia: TRCI",
                identifier = "10.1002/trc2.70197",
                summary = "За двадцать лет наблюдения: с повторными курсами — отношение рисков " +
                    "0,75; без повторных курсов — 1,01, то есть эффекта нет.",
            ),
            Citation(
                key = "edwards2017speed",
                authors = "Edwards J. D. и соавт.",
                year = 2017,
                title = "Speed of processing training results in lower risk of dementia",
                journal = "Alzheimer's & Dementia: TRCI",
                identifier = "S2352873717300598",
                summary = "За десять лет наблюдения риск был ниже на 29 %, и каждое " +
                    "дополнительное занятие было связано с ещё меньшим риском.",
            ),
            Citation(
                key = "ball2002active",
                authors = "Ball K. и соавт.",
                year = 2002,
                title = "Effects of cognitive training interventions with older adults",
                journal = "JAMA",
                identifier = "10.1001/jama.288.18.2271",
                summary = "Исходное исследование, в котором сравнили три вида тренировки " +
                    "с отсутствием занятий.",
            ),
        ),
    )

    private val anchor = EvidenceCard(
        tier = EvidenceTier.B,
        headline = "Повторение с растущими промежутками — способ удержать в памяти " +
            "конкретные важные вещи, а не «память вообще».",
        whatWasStudied = "Разнесённое воспроизведение: одно и то же нужно вспомнить через " +
            "всё более длинные промежутки времени. В сочетании с безошибочным научением — когда " +
            "человеку не дают произнести неверный ответ.",
        inWhom = "Люди с лёгкими и умеренными нарушениями памяти, включая деменцию. " +
            "Много небольших исследований и несколько обзоров.",
        whatWasFound = "Люди действительно запоминали и удерживали конкретные сведения — " +
            "имена, расположение вещей, порядок действий, — которые до этого забывали.",
        caveat = "Многие работы построены на одном-двух участниках, и наблюдение редко " +
            "продолжалось дольше месяца. И главное: это помогает запомнить именно то, " +
            "что вы сюда внесли. На память в целом это не переносится, и мы не будем " +
            "утверждать обратного.",
        citations = listOf(
            Citation(
                key = "sr2023meta",
                authors = "Систематический обзор и метаанализ",
                year = 2023,
                title = "Spaced Retrieval Effects on Learning Capacity in Patients With " +
                    "Mild-to-Moderate Cognitive Impairment",
                journal = "European Psychologist",
                identifier = "10.1027/1016-9040/a000510",
                summary = "Сведение результатов по разнесённому воспроизведению при лёгких " +
                    "и умеренных нарушениях.",
            ),
            Citation(
                key = "fsrs",
                authors = "Open Spaced Repetition",
                year = 2026,
                title = "FSRS — Free Spaced Repetition Scheduler",
                journal = "Открытый проект",
                identifier = "github.com/open-spaced-repetition",
                summary = "Алгоритм расчёта промежутков, настроенный на сотнях миллионов " +
                    "повторений. Мы используем его, но с более короткими промежутками и " +
                    "более высокой целевой надёжностью припоминания.",
            ),
        ),
    )

    private val breathing = EvidenceCard(
        tier = EvidenceTier.B,
        headline = "Медленное дыхание на вашей собственной частоте заметно влияет на " +
            "работу сердца и сосудов. На память — данные противоречивы.",
        whatWasStudied = "Дыхание примерно шесть раз в минуту, на индивидуально подобранной " +
            "частоте, с обратной связью по пульсу.",
        inWhom = "Взрослые и пожилые люди, курсы от нескольких недель.",
        whatWasFound = "Устойчиво растёт вариабельность сердечного ритма. У пожилых после " +
            "пяти недель занятий менялась активность в лобных отделах мозга и таламусе — " +
            "это связывают с состоянием сосудов мозга.",
        caveat = "Влияние именно на память и мышление в исследованиях непостоянно, а от " +
            "одного занятия его никто не показал. Мы включили это ради спокойствия и " +
            "работы сосудов, а не как упражнение для памяти, и не будем называть его так.",
        citations = listOf(
            Citation(
                key = "lehrer2020resonance",
                authors = "Lehrer P. и соавт.",
                year = 2020,
                title = "A Practical Guide to Resonance Frequency Assessment for HRV Biofeedback",
                journal = "Frontiers in Neuroscience",
                identifier = "PMC7578229",
                summary = "Протокол подбора индивидуальной частоты дыхания, который мы здесь " +
                    "и воспроизводим.",
            ),
            Citation(
                key = "nashiro2023hrvb",
                authors = "Nashiro K. и соавт.",
                year = 2023,
                title = "HRV biofeedback modulates physiological influences on the BOLD signal " +
                    "in older adults",
                journal = "Исследование на МРТ",
                identifier = "PMC10736492",
                summary = "После пяти недель занятий у пожилых изменилось влияние " +
                    "физиологических ритмов на сигнал МРТ в лобной коре и таламусе.",
            ),
        ),
    )

    private val movement = EvidenceCard(
        tier = EvidenceTier.B,
        headline = "Физическая нагрузка — из всего списка самое надёжное, что можно сделать " +
            "для мышления. Разные виды нагрузки действуют по-разному.",
        whatWasStudied = "Силовые, аэробные, координационные и смешанные занятия, сравнение " +
            "между собой в сетевых метаанализах.",
        inWhom = "Пожилые люди, в том числе с лёгкими когнитивными нарушениями. Десятки " +
            "рандомизированных исследований.",
        whatWasFound = "Силовые занятия дали наибольший эффект для общего показателя мышления " +
            "и самоконтроля, аэробные — для памяти, координационные — для переключения " +
            "внимания. При лёгких нарушениях лучше всего показал себя смешанный комплекс.",
        caveat = "Всё это — про занятия под присмотром, а не про напоминание в телефоне. " +
            "Приложение может задать ритм и вести счёт, но не может заменить инструктора " +
            "и не проверяет, как вы выполняете упражнение. Каждое упражнение на равновесие " +
            "здесь выполняется с опорой.",
        citations = listOf(
            Citation(
                key = "nma2025exercise",
                authors = "Сетевой метаанализ",
                year = 2025,
                title = "Optimal exercise interventions for enhancing cognitive function in older adults",
                journal = "Frontiers in Aging Neuroscience",
                identifier = "10.3389/fnagi.2025.1510773",
                summary = "Сравнение видов нагрузки между собой по влиянию на разные стороны " +
                    "мышления.",
            ),
        ),
    )

    private val rhythm = EvidenceCard(
        tier = EvidenceTier.C,
        headline = "Стимуляция 40 Гц — направление ранних исследований. Обнадёживает, " +
            "но говорить о действии рано.",
        whatWasStudied = "Свет, мигающий сорок раз в секунду, и звук, пульсирующий с той же " +
            "частотой. Ежедневно, около часа.",
        inWhom = "У мышей — подробно. У людей — небольшие исследования: в основной работе " +
            "участвовало пятнадцать человек с лёгкой деменцией, три месяца.",
        whatWasFound = "У мышей уменьшалось накопление патологического белка. У людей в этой " +
            "небольшой работе за три месяца меньше уменьшался объём мозга, лучше " +
            "восстанавливался суточный ритм и немного лучше вспоминались имена по лицам.",
        caveat = "Пятнадцать человек — это очень мало. Часть более поздних наблюдений велась " +
            "без группы сравнения, то есть ожидания участников могли повлиять на результат. " +
            "Это эксперимент, а не лечение. Кроме того, телефон не может воспроизвести " +
            "аппаратуру из исследований: свет здесь заметно слабее и занимает меньшую часть " +
            "поля зрения. Световой канал требует отдельного согласия и доступен не на всех " +
            "экранах.",
        citations = listOf(
            Citation(
                key = "chan2022plosone",
                authors = "Chan D. и соавт.",
                year = 2022,
                title = "Gamma frequency sensory stimulation in mild probable Alzheimer's " +
                    "dementia patients",
                journal = "PLOS ONE",
                identifier = "10.1371/journal.pone.0278412",
                summary = "Пилотное исследование на пятнадцати участниках: стимуляция " +
                    "переносилась хорошо, наблюдались изменения на МРТ и в суточном ритме.",
            ),
            Citation(
                key = "iaccarino2016nature",
                authors = "Iaccarino H. F. и соавт.",
                year = 2016,
                title = "Gamma frequency entrainment attenuates amyloid load and modifies microglia",
                journal = "Nature",
                identifier = "10.1038/nature20587",
                summary = "У мышей именно 40 Гц, а не другие частоты, уменьшали накопление " +
                    "амилоида.",
            ),
        ),
    )

    private val assessment = EvidenceCard(
        tier = EvidenceTier.A,
        headline = "Короткие пробы на своём телефоне достаточно надёжны, чтобы следить " +
            "за собственной динамикой — если делать их сериями.",
        whatWasStudied = "Самостоятельные короткие когнитивные пробы на личном смартфоне, " +
            "по нескольку раз за несколько дней подряд.",
        inWhom = "268 пожилых людей без нарушений (от 65 до 97 лет) и 22 человека с очень " +
            "лёгкой деменцией.",
        whatWasFound = "Повторяемость результатов через полгода и через год превышала 0,85. " +
            "Итоговый показатель совпадал с обычным очным обследованием примерно так же " +
            "хорошо, как эти обследования совпадают между собой. Люди охотно проходили " +
            "пробы: до конца дошли четверо из пяти.",
        caveat = "Надёжен сам подход, а не наша конкретная батарея проб — она не проходила " +
            "отдельной проверки и сравнивать её не с чем. Поэтому мы никогда не показываем " +
            "«ваш результат среди других людей» и не ставим никаких оценок: только ваше " +
            "изменение относительно вас самих, и только когда оно выходит за пределы " +
            "вашей обычной изменчивости.",
        citations = listOf(
            Citation(
                key = "nicosia2023arc",
                authors = "Nicosia J. и соавт.",
                year = 2023,
                title = "Unsupervised high-frequency smartphone-based cognitive assessments " +
                    "are reliable, valid, and feasible in older adults",
                journal = "J. Int. Neuropsychol. Soc.",
                identifier = "PMC9985662",
                summary = "Короткие самостоятельные пробы на телефоне у людей 65–97 лет " +
                    "оказались повторяемыми и согласованными с очным обследованием.",
            ),
        ),
    )
}
