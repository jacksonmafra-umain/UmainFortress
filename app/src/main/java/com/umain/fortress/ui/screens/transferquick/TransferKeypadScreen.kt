package com.umain.fortress.ui.screens.transferquick

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.umain.fortress.ui.components.PrimaryButton
import com.umain.fortress.ui.icons.FortressIcons
import com.umain.fortress.ui.theme.FortressTheme
import com.umain.fortress.ui.theme.MoneyDisplay

/**
 * Quick-amount keypad reached from the Send pill on the Dashboard. Pure UI scaffold — does
 * not yet wire into the existing biometric-signed Transfer flow.
 *
 * Reference: Vault keypad mock — large numeric grid + lavender "Transfer" pill below.
 */
@Composable
fun TransferKeypadScreen(
    onContinue: (amountString: String) -> Unit,
    onBack: () -> Unit,
) {
    var amount by remember { mutableStateOf("0") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = FortressIcons.Back,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                text = "New transfer",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        Box(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = formatAmount(amount),
                style = MoneyDisplay,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            KeypadRow(listOf("1", "2", "3"), onClick = { amount = pushDigit(amount, it) })
            KeypadRow(listOf("4", "5", "6"), onClick = { amount = pushDigit(amount, it) })
            KeypadRow(listOf("7", "8", "9"), onClick = { amount = pushDigit(amount, it) })
            KeypadRow(
                listOf(".", "0", "←"),
                onClick = { key ->
                    amount = when (key) {
                        "." -> if (amount.contains('.')) amount else "$amount."
                        "←" -> amount.dropLast(1).ifEmpty { "0" }
                        else -> pushDigit(amount, key)
                    }
                },
            )
            PrimaryButton(
                text = "Transfer",
                onClick = { onContinue(amount) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun KeypadRow(keys: List<String>, onClick: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        keys.forEach { key ->
            KeypadKey(key = key, onClick = { onClick(key) }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun KeypadKey(key: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(vertical = 4.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.large,
            )
            .clickable(onClick = onClick)
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = key,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun pushDigit(current: String, digit: String): String {
    if (current == "0" && digit != ".") return digit
    if (current.contains('.')) {
        val dec = current.substringAfter('.')
        if (dec.length >= 2) return current
    }
    return current + digit
}

@Composable
private fun formatAmount(raw: String): AnnotatedString = buildAnnotatedString {
    val (head, tail) = if (raw.contains('.')) {
        raw.substringBefore('.') to "." + raw.substringAfter('.').padEnd(2, '0').take(2)
    } else {
        raw to ".00"
    }
    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onBackground)) {
        append("$")
        append(head)
    }
    withStyle(SpanStyle(color = FortressTheme.colors.moneyTail, fontSize = MaterialTheme.typography.headlineMedium.fontSize)) {
        append(tail)
    }
}
