package com.tored.bridgelauncher

import android.app.Activity
import android.app.StatusBarManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ProvideTextStyle
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tored.bridgelauncher.annotations.Display
import com.tored.bridgelauncher.composables.Btn
import com.tored.bridgelauncher.composables.ResIcon
import com.tored.bridgelauncher.composables.Tip
import com.tored.bridgelauncher.services.BridgeButtonQSTileService
import com.tored.bridgelauncher.services.BridgeLauncherDeviceAdminReceiver
import com.tored.bridgelauncher.settings.SettingsState
import com.tored.bridgelauncher.settings.SettingsVM
import com.tored.bridgelauncher.settings.writeBool
import com.tored.bridgelauncher.settings.writeEnum
import com.tored.bridgelauncher.settings.writeUri
import com.tored.bridgelauncher.ui.shared.CheckboxField
import com.tored.bridgelauncher.ui.shared.OptionsRow
import com.tored.bridgelauncher.ui.shared.SetSystemBarsForBotBarActivity
import com.tored.bridgelauncher.ui.theme.BridgeLauncherTheme
import com.tored.bridgelauncher.ui.theme.borders
import com.tored.bridgelauncher.ui.theme.botBar
import com.tored.bridgelauncher.ui.theme.textSec
import com.tored.bridgelauncher.utils.RawRepresentable
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import kotlin.reflect.KProperty1
import kotlin.reflect.full.findAnnotation

enum class SystemBarAppearanceOptions(override val rawValue: Int) : RawRepresentable<Int>
{
    Hide(0),
    LightIcons(1),
    DarkIcons(2),
}

enum class ThemeOptions(override val rawValue: Int) : RawRepresentable<Int>
{
    System(0),
    Light(1),
    Dark(2),
}

@AndroidEntryPoint
class SettingsActivity : ComponentActivity()
{
    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)

        setContent {
            BridgeLauncherTheme()
            {
                SettingsScreen()
            }
        }
    }
}

fun <TProp> displayNameFor(prop: KProperty1<SettingsState, TProp>): String
{
    val ann = prop.findAnnotation<Display>()
    return ann?.name ?: prop.name
}

class OpenDocumentTreePlus : ActivityResultContracts.OpenDocumentTree()
{
    override fun createIntent(context: Context, input: Uri?): Intent
    {
        return super.createIntent(context, input).also {
            it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            it.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
//            it.addCategory(Intent.CATEGORY_OPENABLE)
        }
    }
}

@Composable
fun SettingsScreen(vm: SettingsVM = viewModel())
{
    val uiState by vm.settingsUIState.collectAsStateWithLifecycle()
    LaunchedEffect(vm) { vm.request() }

    val context = LocalContext.current
    val adminReceiverComponentName = ComponentName(context, BridgeLauncherDeviceAdminReceiver::class.java)

    val launcher = rememberLauncherForActivityResult(
        contract = OpenDocumentTreePlus(),
        onResult = { uri ->
            if (uri != null)
            {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

            vm.edit {
                writeUri(SettingsState::currentProjUri, uri)
            }
        }
    )

    @Composable
    fun checkboxFieldFor(prop: KProperty1<SettingsState, Boolean>)
    {
        CheckboxField(
            label = displayNameFor(prop),
            isChecked = prop.getValue(uiState, prop),
            onCheckedChange = { isChecked ->
                vm.edit {
                    writeBool(prop, isChecked)
                }
            }
        )
    }

    @Composable
    fun systemBarOptionsFieldFor(prop: KProperty1<SettingsState, SystemBarAppearanceOptions>, vm: SettingsVM = viewModel())
    {
        SystemBarAppearanceOptionsField(
            label = displayNameFor(prop),
            selectedOption = prop.getValue(uiState, prop),
            onChange = { value ->
                vm.edit {
                    writeEnum(prop, value)
                }
            }
        )
    }

    SetSystemBarsForBotBarActivity()

    Surface(
        color = MaterialTheme.colors.background
    )
    {
        Column()
        {

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(0.dp, 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            )
            {
                SettingsSection(label = "Project", iconResId = R.drawable.ic_open_folder)
                {
                    CurrentProjectCard(uiState.currentProjUri?.lastPathSegment)
                    {
                        launcher.launch(File(Environment.getExternalStorageDirectory(), "BridgeLauncherProjects").toUri())
                    }

                    val prop = SettingsState::allowProjectsToTurnScreenOff
                    CheckboxField(
                        label = displayNameFor(prop),
                        description = if (uiState.isDeviceAdminEnabled) "Bridge has device admin permissions." else "Tap to grant Bridge device admin permissions.",
                        isChecked = prop.getValue(uiState, prop),
                        onCheckedChange = { isChecked ->
                            if (uiState.isDeviceAdminEnabled)
                            {
                                vm.edit {
                                    writeBool(prop, isChecked)
                                }
                            }
                            else
                            {
                                context.startActivity(
                                    Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                        putExtra(
                                            DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                                            adminReceiverComponentName
                                        )
                                        putExtra(
                                            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                            "Bridge Launcher needs this permission so projects can request the screen to be locked."
                                        )
                                    }
                                )
                            }
                        }
                    )

//                    Btn(text = "Lock the screen")
//                    {
//                        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
//                        if (dpm.isAdminActive(adminReceiverComponentName))
//                        {
//                            dpm.lockNow()
//                        }
//                        else
//                        {
//                            Toast
//                                .makeText(
//                                    context,
//                                    "Bridge is not a device admin. Visit Bridge settings to resolve this issue.",
//                                    Toast.LENGTH_LONG
//                                )
//                                .show()
//                        }
//                    }
                }

                Divider()

                SettingsSection(label = "System wallpaper", iconResId = R.drawable.ic_image)
                {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    )
                    {
                        Btn(
                            modifier = Modifier
                                .fillMaxWidth(),
                            text = "Set system wallpaper",
                            outlined = true,
                            onClick = { context.startActivity(Intent(Intent.ACTION_SET_WALLPAPER)) },
                        )

                        checkboxFieldFor(SettingsState::drawSystemWallpaperBehindWebView)
                    }
                }

                Divider()

                SettingsSection(label = "Overlays", iconResId = R.drawable.ic_overlays)
                {
                    systemBarOptionsFieldFor(SettingsState::statusBarAppearance)
                    systemBarOptionsFieldFor(SettingsState::navigationBarAppearance)
                    checkboxFieldFor(SettingsState::drawWebViewOverscrollEffects)
                }

                Divider()

                SettingsSection(label = "Bridge", iconResId = R.drawable.ic_bridge)
                {
                    OptionsRow(
                        label = "Theme",
                        options = mapOf(
                            ThemeOptions.System to "System",
                            ThemeOptions.Light to "Light",
                            ThemeOptions.Dark to "Dark",
                        ),
                        selectedOption = uiState.theme,
                        onChange = { theme ->
                            vm.edit {
                                writeEnum(SettingsState::theme, theme)
                            }
                        },
                    )

                    checkboxFieldFor(SettingsState::showBridgeButton)

                    ProvideTextStyle(value = MaterialTheme.typography.body2.copy(color = MaterialTheme.colors.textSec))
                    {
                        Tip("Tap and hold the button to move it.")
                    }

                    checkboxFieldFor(SettingsState::showLaunchAppsWhenBridgeButtonCollapsed)

                    if (!uiState.isQSTileAdded)
                    {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                        {
                            ActionCard(
                                title = "Quick settings tile",
                                description = "You can add a quick settings tile to unobtrusively toggle the Bridge button. Long-pressing the tile opens this settings screen."
                            )
                            {
                                val sbm = context.getSystemService(StatusBarManager::class.java)
                                val compName = ComponentName(context, BridgeButtonQSTileService::class.java)

                                Btn(text = "Add tile", suffixIcon = R.drawable.ic_plus, onClick = {
                                    sbm.requestAddTileService(
                                        compName,
                                        "Bridge button",
                                        Icon.createWithResource(context, R.drawable.ic_bridge_white),
                                        {},
                                        {}
                                    )
                                })
                            }
                        }
                        else
                        {
                            ActionCard(
                                title = "Quick settings tile",
                                description = "You can add a quick settings tile to unobtrusively toggle the Bridge button. Long-pressing the tile opens this settings screen.\n"
                                        + "\n"
                                        + "Quick settings are the toggles in your notifications area that you use to toggle for example WiFi or Bluetooth. "
                                        + "To add the Bridge button tile, expand the quick settings panel and look for an edit button."
                            )
                        }
                    }
                    else
                    {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .border(MaterialTheme.borders.soft, MaterialTheme.shapes.medium)
                                .padding(start = 12.dp, top = 16.dp, bottom = 16.dp, end = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        )
                        {
                            ResIcon(R.drawable.ic_check, color = MaterialTheme.colors.primary)
                            Text("Quick settings tile added.")
                        }
                    }
                }

                Divider()

                SettingsSection(label = "Development", iconResId = R.drawable.ic_tools)
                {
                    ActionCard(
                        title = "Bridge developer hub",
                        description = "Documentation and tools to help you develop Bridge Launcher projects."
                    )
                    {
                        Btn(text = "Open in browser", suffixIcon = R.drawable.ic_open_in_new, onClick = { /* TODO */ })
                    }

                    ActionCard(
                        title = "Export installed apps",
                        description = "Create a folder with information about apps installed on this phone, including icons. You can use this folder to work on projects from your PC."
                    )
                    {
                        Btn(text = "Export", suffixIcon = R.drawable.ic_save_to_device, onClick = { /* TODO */ })
                    }
                }

                Divider()

                SettingsSection(label = "About & Contact", iconResId = R.drawable.ic_about)
                {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalAlignment = Alignment.End,
                    )
                    {
                        Btn(text = "GitHub repository", suffixIcon = R.drawable.ic_open_in_new, onClick = { /* TODO */ })
                        Btn(text = "Discord server", suffixIcon = R.drawable.ic_open_in_new, onClick = { /* TODO */ })
                        Btn(text = "Send me an email", suffixIcon = R.drawable.ic_arrow_right, onClick = { /* TODO */ })
                        Btn(text = "Copy my email address", suffixIcon = R.drawable.ic_copy, onClick = { /* TODO */ })
                    }
                }
            }

            SettingsBotBar()

        }
    }
}

@Composable
fun SettingsBotBar(modifier: Modifier = Modifier)
{
    Surface(
        color = MaterialTheme.colors.surface,
        modifier = modifier
            .height(56.dp)
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.botBar,
        elevation = 4.dp,
    )
    {
        Row(
            modifier = Modifier
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        )
        {
            val context = LocalContext.current as Activity

            IconButton(onClick = { context.finish() })
            {
                ResIcon(R.drawable.ic_arrow_left)
            }
            Text(
                modifier = Modifier
                    .weight(1f)
                    .wrapContentHeight(),
                text = "Settings",
                style = MaterialTheme.typography.h6,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.size(48.dp))
//                IconToggleButton(
//                    checked = MaterialTheme.colors.isLight,
//                    onCheckedChange = { /* TODO */ }
//                )
//                {
//                    ResIcon(iconResId = R.drawable.ic_dark_mode)
//                }
        }
    }
}

typealias ComposableContent = @Composable () -> Unit

@Composable
fun SettingsSection(label: String, iconResId: Int, content: ComposableContent)
{
    Column(
        modifier = Modifier
            .padding(bottom = 8.dp)
            .fillMaxWidth(),
    )
    {
        SettingsSectionHeader(label = label, iconResId = iconResId)
        Column(
            modifier = Modifier
                .padding(start = 16.dp, top = 8.dp, bottom = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        )
        {
            content()
        }
    }
}

@Composable
fun SettingsSectionHeader(label: String, iconResId: Int)
{
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .padding(20.dp, 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp, alignment = Alignment.Start),
    )
    {
        ResIcon(iconResId = iconResId)
        Text(
            label,
            style = MaterialTheme.typography.h6,
        )
    }
}

@Composable
fun CurrentProjectCard(currentProjName: String?, onChangeClick: () -> Unit)
{
    Surface(
        modifier = Modifier
            .border(border = MaterialTheme.borders.soft, shape = MaterialTheme.shapes.medium)
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
    )
    {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        )
        {
            Column(
                modifier = Modifier.weight(1f)
            )
            {
                Text("Current project", style = MaterialTheme.typography.body2, color = MaterialTheme.colors.textSec)
                Text(currentProjName ?: "-", style = MaterialTheme.typography.body1)
            }
            Btn(text = "Change", onClick = onChangeClick)
        }
    }
}

@Composable
fun ActionCard(title: String, description: String, footerContent: ComposableContent? = null)
{
    val pad = 24.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .border(MaterialTheme.borders.soft, MaterialTheme.shapes.large),
    )
    {
        Column(
            modifier = Modifier
                .padding(
                    top = pad,
                    start = pad,
                    end = pad,
                    bottom = if (footerContent == null) pad else 0.dp
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        )
        {
            Text(title)
            Text(description, style = MaterialTheme.typography.body2, color = MaterialTheme.colors.textSec)
        }

        if (footerContent != null)
        {
            Row(
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth()
                    .wrapContentHeight(),
                horizontalArrangement = Arrangement.spacedBy(4.dp, alignment = Alignment.End)
            )
            {
                footerContent()
            }
        }
    }
}


@Composable
fun SystemBarAppearanceOptionsField(label: String, selectedOption: SystemBarAppearanceOptions, onChange: (SystemBarAppearanceOptions) -> Unit)
{
    OptionsRow(
        label = label,
        options = mapOf(
            SystemBarAppearanceOptions.Hide to "Hide",
            SystemBarAppearanceOptions.LightIcons to "Light icons",
            SystemBarAppearanceOptions.DarkIcons to "Dark icons",
        ),
        selectedOption = selectedOption,
        onChange = onChange
    )
}


@Composable
@Preview(showBackground = true)
fun SettingsPreview()
{
    BridgeLauncherTheme {
        SystemBarAppearanceOptionsField("Status bar", SystemBarAppearanceOptions.Hide, { })
    }
}