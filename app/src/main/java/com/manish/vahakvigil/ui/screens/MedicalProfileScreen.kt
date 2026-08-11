package com.manish.vahakvigil.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.manish.vahakvigil.ui.components.AppCard
import com.manish.vahakvigil.ui.components.AppScreen
import com.manish.vahakvigil.ui.components.AppTextField
import com.manish.vahakvigil.ui.components.AppTopBar
import com.manish.vahakvigil.ui.components.PrimaryButton
import com.manish.vahakvigil.ui.components.SectionTitle
import com.manish.vahakvigil.ui.theme.VahakVigilGradientBackground
import com.manish.vahakvigil.ui.theme.VahakVigilTheme

@Composable
fun MedicalProfileScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf("Manish Sharma") }
    var age by remember { mutableStateOf("29") }
    var bloodGroup by remember { mutableStateOf("O+") }
    var conditions by remember { mutableStateOf("No chronic conditions") }
    var allergies by remember { mutableStateOf("None known") }
    var medications by remember { mutableStateOf("No active medication") }
    var height by remember { mutableStateOf("178 cm") }
    var weight by remember { mutableStateOf("74 kg") }
    var notes by remember { mutableStateOf("Notify primary contact before hospital transfer.") }

    AppScreen(modifier = modifier) {
        AppTopBar(title = "Medical", onBackClick = onBackClick)

        Spacer(modifier = Modifier.height(26.dp))

        SectionTitle(text = "Medical Profile")

        Spacer(modifier = Modifier.height(16.dp))

        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                AppTextField("Name", name, { name = it })
                Spacer(modifier = Modifier.height(16.dp))
                AppTextField("Age", age, { age = it })
                Spacer(modifier = Modifier.height(16.dp))
                AppTextField("Blood Group", bloodGroup, { bloodGroup = it })
                Spacer(modifier = Modifier.height(16.dp))
                AppTextField("Emergency Conditions", conditions, { conditions = it }, minLines = 2)
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        SectionTitle(text = "Care Details")

        Spacer(modifier = Modifier.height(16.dp))

        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                AppTextField("Allergies", allergies, { allergies = it }, minLines = 2)
                Spacer(modifier = Modifier.height(16.dp))
                AppTextField("Medications", medications, { medications = it }, minLines = 2)
                Spacer(modifier = Modifier.height(16.dp))
                AppTextField("Height", height, { height = it })
                Spacer(modifier = Modifier.height(16.dp))
                AppTextField("Weight", weight, { weight = it })
                Spacer(modifier = Modifier.height(16.dp))
                AppTextField("Emergency Notes", notes, { notes = it }, minLines = 3)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        PrimaryButton(
            text = "Save medical profile",
            onClick = {},
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun MedicalProfileScreenPreview() {
    VahakVigilTheme {
        VahakVigilGradientBackground {
            MedicalProfileScreen(onBackClick = {})
        }
    }
}
