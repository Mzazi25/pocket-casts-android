package au.com.shiftyjelly.pocketcasts.settings.plus

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import au.com.shiftyjelly.pocketcasts.compose.bars.NavigationButton
import au.com.shiftyjelly.pocketcasts.compose.bars.ThemedTopAppBar
import au.com.shiftyjelly.pocketcasts.compose.buttons.RowButton
import au.com.shiftyjelly.pocketcasts.compose.buttons.RowOutlinedButton
import au.com.shiftyjelly.pocketcasts.compose.components.TextH20
import au.com.shiftyjelly.pocketcasts.compose.components.TextH40
import au.com.shiftyjelly.pocketcasts.compose.components.TextP40
import au.com.shiftyjelly.pocketcasts.compose.images.VerticalLogoPlus
import au.com.shiftyjelly.pocketcasts.compose.text.LinkText
import au.com.shiftyjelly.pocketcasts.compose.theme
import au.com.shiftyjelly.pocketcasts.settings.R
import au.com.shiftyjelly.pocketcasts.settings.viewmodel.UpgradeAccountViewModel
import au.com.shiftyjelly.pocketcasts.localization.R as LR

@Composable
fun PlusUpgradePage(
    onCloseClick: () -> Unit,
    onUpgradeClick: () -> Unit,
    onLearnMoreClick: () -> Unit,
    featureBlocked: Boolean,
    storageLimitGb: Long,
    viewModel: UpgradeAccountViewModel
) {
    val priceState by viewModel.productState.observeAsState()
    Column(modifier = Modifier.background(MaterialTheme.theme.colors.primaryUi02)) {
        ThemedTopAppBar(
            title = "",
            navigationButton = NavigationButton.Close,
            onNavigationClick = onCloseClick,
            backgroundColor = MaterialTheme.theme.colors.primaryUi02
        )
        PlusInformation(
            storageLimitGb = storageLimitGb,
            price = priceState?.get(),
            onLearnMoreClick = onLearnMoreClick,
            featureBlocked = featureBlocked,
            modifier = Modifier.weight(1f),
        )
        ButtonPanel(
            onUpgradeClick = onUpgradeClick,
            onCloseClick = onCloseClick
        )
    }
}

@Composable
fun ButtonPanel(
    onUpgradeClick: () -> Unit,
    onCloseClick: () -> Unit
) {
    Surface(
        elevation = 8.dp,
        color = MaterialTheme.theme.colors.primaryUi02
    ) {
        Column {
            RowButton(
                text = stringResource(LR.string.profile_upgrade_to_plus),
                onClick = onUpgradeClick,
                includePadding = false,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp)
            )
            RowOutlinedButton(
                text = stringResource(LR.string.profile_create_tos_disagree),
                onClick = onCloseClick,
                includePadding = false,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun PlusInformation(
    storageLimitGb: Long,
    price: String?,
    onLearnMoreClick: () -> Unit,
    featureBlocked: Boolean,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(vertical = 16.dp)
            .fillMaxWidth()
    ) {
        Spacer(modifier = Modifier.height(10.dp))
        VerticalLogoPlus()
        Spacer(modifier = Modifier.height(32.dp))
        TextH20(
            text = stringResource(if (featureBlocked) LR.string.profile_feature_requires else LR.string.profile_help_support),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (storageLimitGb > 0) {
            PlusFeatureList(
                storageLimitGb = storageLimitGb
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        LinkText(
            text = stringResource(LR.string.plus_learn_more_about_plus),
            onClick = onLearnMoreClick
        )
        Spacer(modifier = Modifier.height(6.dp))
        if (price != null) {
            TextH40(
                text = stringResource(LR.string.plus_per_month, price),
                color = MaterialTheme.theme.colors.primaryText02
            )
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
private fun PlusFeatureList(storageLimitGb: Long) {
    Column(modifier = Modifier.padding(end = 32.dp)) {
        PlusFeature(text = stringResource(id = LR.string.profile_web_player))
        PlusFeature(text = stringResource(id = LR.string.profile_extra_themes))
        PlusFeature(text = stringResource(id = LR.string.profile_extra_app_icons))
        PlusFeature(text = stringResource(id = LR.string.plus_cloud_storage_limit, storageLimitGb))
        PlusFeature(text = stringResource(id = LR.string.folders))
    }
}

@Composable
private fun PlusFeature(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 5.dp)) {
        Image(
            painter = painterResource(R.drawable.ic_plus),
            contentDescription = null,
            modifier = Modifier.padding(end = 16.dp)
        )
        TextP40(
            text = text,
            color = MaterialTheme.theme.colors.primaryText02
        )
    }
}