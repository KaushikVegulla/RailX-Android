package com.kaushik.railway.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaushik.railway.data.WlEstimate
import com.kaushik.railway.ui.components.RailCard
import com.kaushik.railway.ui.theme.Navy
import com.kaushik.railway.ui.theme.Orange

/**
 * The waitlist confirmation estimate card, shared by the PNR screen
 * and the Trip Day companion. Always labelled as a RailX estimate.
 */
@Composable
fun WlEstimateCard(estimate: WlEstimate, modifier: Modifier = Modifier) {
    RailCard(modifier) {
        Column {
            Text("Confirmation chances", fontWeight = FontWeight.Bold, color = Navy, fontSize = 16.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                "${estimate.band} · about ${estimate.percent}%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Orange
            )
            estimate.factors.forEach { factor ->
                Text(
                    "• $factor",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Text(
                "RailX estimate from the queue position, class, quota and days left. Not official railway data.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
