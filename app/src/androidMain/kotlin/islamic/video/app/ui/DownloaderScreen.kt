package islamic.video.app.ui

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.activity.compose.BackHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import islamic.video.app.R
import islamic.video.app.ui.theme.AppTheme
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.School

@Composable
fun CustomNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .height(32.dp)
                .width(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            CompositionLocalProvider(
                LocalContentColor provides if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                icon()
            }
        }
        Spacer(Modifier.height(4.dp))
        CompositionLocalProvider(
            LocalContentColor provides if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        ) {
            ProvideTextStyle(value = MaterialTheme.typography.labelSmall.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)) {
                label()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloaderScreen(viewModel: DownloaderViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var playingUrl by remember { mutableStateOf<String?>(null) }
    var channelUrl by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    if (channelUrl != null) {
        ChannelScreen(
            url = channelUrl!!,
            onBack = { channelUrl = null },
            onVideoSelected = { url, _, _ ->
                playingUrl = url
                channelUrl = null
            }
        )
        return
    }

    if (playingUrl != null) {
        PlayerScreen(
            url = playingUrl!!,
            viewModel = viewModel,
            onBack = { playingUrl = null },
            onChannelSelected = { url ->
                channelUrl = url
            }
        )
        return
    }

    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    val navigationContent: @Composable () -> Unit = {
        if (isTablet) {
            NavigationRail(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Spacer(Modifier.weight(1f))
                NavigationRailItem(
                    selected = selectedTab == 0 && !showSettings,
                    onClick = {
                        showSettings = false
                        viewModel.activeFilter.value = "Search"
                        selectedTab = 0
                    },
                    icon = {
                        if (selectedTab == 0 && !showSettings) {
                            Icon(Icons.Filled.Home, contentDescription = "Home")
                        } else {
                            Icon(Icons.Outlined.Home, contentDescription = "Home")
                        }
                    },
                    label = { Text("Home") }
                )
                NavigationRailItem(
                    selected = selectedTab == 1 && !showSettings,
                    onClick = {
                        showSettings = false
                        selectedTab = 1
                    },
                    icon = {
                        Image(
                            painter = painterResource(R.drawable.ic_amau_logo),
                            contentDescription = "AMAU",
                            modifier = Modifier.size(24.dp).clip(CircleShape)
                        )
                    },
                    label = { Text("AMAU") }
                )
                NavigationRailItem(
                    selected = selectedTab == 2 && !showSettings,
                    onClick = {
                        showSettings = false
                        selectedTab = 2
                    },
                    icon = {
                        Image(
                            painter = painterResource(R.drawable.ic_amar_logo),
                            contentDescription = "AMAR",
                            modifier = Modifier.size(24.dp).clip(CircleShape)
                        )
                    },
                    label = { Text("AMAR") }
                )
                NavigationRailItem(
                    selected = selectedTab == 3 && !showSettings,
                    onClick = {
                        showSettings = false
                        selectedTab = 3
                    },
                    icon = {
                        if (selectedTab == 3 && !showSettings) {
                            Icon(Icons.Filled.Translate, contentDescription = "Arabic 101")
                        } else {
                            Icon(Icons.Outlined.Translate, contentDescription = "Arabic 101")
                        }
                    },
                    label = { Text("Arabic 101") }
                )
                NavigationRailItem(
                    selected = selectedTab == 4 && !showSettings,
                    onClick = {
                        showSettings = false
                        selectedTab = 4
                    },
                    icon = {
                        if (selectedTab == 4 && !showSettings) {
                            Icon(Icons.Filled.School, contentDescription = "ilman Nafiya")
                        } else {
                            Icon(Icons.Outlined.School, contentDescription = "ilman Nafiya")
                        }
                    },
                    label = { Text("ilman Nafiya") }
                )
                Spacer(Modifier.weight(1f))
            }
        } else {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                        .height(80.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CustomNavItem(
                        selected = selectedTab == 0 && !showSettings,
                        onClick = {
                            showSettings = false
                            viewModel.activeFilter.value = "Search"
                            selectedTab = 0
                        },
                        icon = {
                            if (selectedTab == 0 && !showSettings) {
                                Icon(Icons.Filled.Home, contentDescription = "Home")
                            } else {
                                Icon(Icons.Outlined.Home, contentDescription = "Home")
                            }
                        },
                        label = { Text("Home") }
                    )
                    CustomNavItem(
                        selected = selectedTab == 1 && !showSettings,
                        onClick = {
                            showSettings = false
                            selectedTab = 1
                        },
                        icon = {
                            Image(
                                painter = painterResource(R.drawable.ic_amau_logo),
                                contentDescription = "AMAU",
                                modifier = Modifier.size(24.dp).clip(CircleShape)
                            )
                        },
                        label = { Text("AMAU") }
                    )
                    CustomNavItem(
                        selected = selectedTab == 2 && !showSettings,
                        onClick = {
                            showSettings = false
                            selectedTab = 2
                        },
                        icon = {
                            Image(
                                painter = painterResource(R.drawable.ic_amar_logo),
                                contentDescription = "AMAR",
                                modifier = Modifier.size(24.dp).clip(CircleShape)
                            )
                        },
                        label = { Text("AMAR") }
                    )
                    CustomNavItem(
                        selected = selectedTab == 3 && !showSettings,
                        onClick = {
                            showSettings = false
                            selectedTab = 3
                        },
                        icon = {
                            if (selectedTab == 3 && !showSettings) {
                                Icon(Icons.Filled.Translate, contentDescription = "Arabic 101")
                            } else {
                                Icon(Icons.Outlined.Translate, contentDescription = "Arabic 101")
                            }
                        },
                        label = { Text("Arabic 101") }
                    )
                    CustomNavItem(
                        selected = selectedTab == 4 && !showSettings,
                        onClick = {
                            showSettings = false
                            selectedTab = 4
                        },
                        icon = {
                            if (selectedTab == 4 && !showSettings) {
                                Icon(Icons.Filled.School, contentDescription = "ilman Nafiya")
                            } else {
                                Icon(Icons.Outlined.School, contentDescription = "ilman Nafiya")
                            }
                        },
                        label = { Text("ilman Nafiya") }
                    )
                }
            }
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        if (isTablet) {
            navigationContent()
        }

        Scaffold(
            modifier = Modifier.weight(1f),
            bottomBar = {
                if (!isTablet) {
                    navigationContent()
                }
            }
        ) { innerPadding ->
            if (showSettings) {
                BackHandler {
                    showSettings = false
                }
            } else if (selectedTab != 0) {
                BackHandler {
                    selectedTab = 0
                }
            }

            val screenMargin = if (isTablet) (configuration.screenWidthDp * 0.20f).dp else 0.dp

            Box(modifier = Modifier.fillMaxSize().padding(horizontal = screenMargin)) {
                if (showSettings) {
                    SettingsTab(viewModel, innerPadding)
                } else {
                    AnimatedContent(targetState = selectedTab, label = "tab_transition") { targetTab ->
                        when (targetTab) {
                            0 -> NewPipeTab(
                                viewModel = viewModel,
                                contentPadding = innerPadding,
                                onUrlSelected = { url -> playingUrl = url },
                                onOpenSettings = { showSettings = true }
                            )
                            1 -> ChannelScreen(
                                url = "https://www.youtube.com/channel/UCmTqZ28TaM7V_g7uJVuBAMw",
                                onBack = null,
                                onVideoSelected = { url, _, _ -> playingUrl = url }
                            )
                            2 -> ChannelScreen(
                                url = "https://www.youtube.com/channel/UCHC1c8i5RMDE-2GhNBCQeVw",
                                onBack = null,
                                onVideoSelected = { url, _, _ -> playingUrl = url }
                            )
                            3 -> ChannelScreen(
                                url = "https://www.youtube.com/channel/UCEcQu_9GNDiGU_vqjVZXj3Q",
                                onBack = null,
                                onVideoSelected = { url, _, _ -> playingUrl = url }
                            )
                            4 -> ChannelScreen(
                                url = "https://www.youtube.com/channel/UCvKlSNZ58rTQ0xrWT1jrqFg",
                                onBack = null,
                                onVideoSelected = { url, _, _ -> playingUrl = url }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.animation.ExperimentalAnimationApi::class)
@Composable
fun SettingsTab(viewModel: DownloaderViewModel, contentPadding: PaddingValues = PaddingValues(0.dp)) {
    var currentScreen by remember { mutableStateOf("Main") }

    BackHandler(enabled = currentScreen != "Main") {
        currentScreen = "Main"
    }

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            if (targetState != "Main") {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300)
                ) togetherWith slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300)
                )
            } else {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300)
                ) togetherWith slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300)
                )
            }
        },
        label = "settings_transition"
    ) { screen ->
        when (screen) {
            "Main" -> SettingsMainList(onNavigate = { currentScreen = it }, contentPadding = contentPadding)
            "Customisation" -> CustomisationScreen(viewModel, onBack = { currentScreen = "Main" }, contentPadding = contentPadding)
            "About" -> AboutScreen(onBack = { currentScreen = "Main" }, contentPadding = contentPadding)
        }
    }
}

@Composable
fun SettingsListItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String,
    topRadius: androidx.compose.ui.unit.Dp = 0.dp,
    bottomRadius: androidx.compose.ui.unit.Dp = 0.dp,
    onClick: () -> Unit
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(if (isPressed) 0.95f else 1f)
    val currentTop by androidx.compose.animation.core.animateDpAsState(if (isPressed) 24.dp else topRadius)
    val currentBottom by androidx.compose.animation.core.animateDpAsState(if (isPressed) 24.dp else bottomRadius)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(
                topStart = currentTop, topEnd = currentTop,
                bottomStart = currentBottom, bottomEnd = currentBottom
            ))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(
                interactionSource = interactionSource, 
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(iconColor, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White)
        }
        Spacer(Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SettingsMainList(onNavigate: (String) -> Unit, contentPadding: PaddingValues) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 16.dp)
        )
        
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            SettingsListItem(
                icon = Icons.Default.Palette,
                iconColor = Color(0xFF88637B),
                title = "Customisation",
                subtitle = "Themes & App Appearance",
                topRadius = 24.dp,
                onClick = { onNavigate("Customisation") }
            )
            SettingsListItem(
                icon = Icons.Default.Info,
                iconColor = Color(0xFF4C6B8B),
                title = "About",
                subtitle = "Developer info & Credits",
                onClick = { onNavigate("About") }
            )
            
            val packageInfo = remember {
                try {
                    context.packageManager.getPackageInfo(context.packageName, 0)
                } catch (e: Exception) {
                    null
                }
            }
            val versionName = packageInfo?.versionName ?: "1.0.0"

            SettingsListItem(
                icon = Icons.Default.Info,
                iconColor = Color(0xFF6B8B4C),
                title = "App Info",
                subtitle = "Version $versionName",
                bottomRadius = 24.dp,
                onClick = {}
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomisationScreen(viewModel: DownloaderViewModel, onBack: () -> Unit, contentPadding: PaddingValues) {
    val themeMode = viewModel.themeMode.intValue
    val selectedTheme = viewModel.selectedTheme.value
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 16.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Spacer(Modifier.width(8.dp))
            Text("Customisation", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        
        Card {
            Column(Modifier.padding(16.dp)) {
                Text("Theme Mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = themeMode == 0, onClick = { viewModel.setThemeMode(0) }, label = { Text("System") })
                    FilterChip(selected = themeMode == 1, onClick = { viewModel.setThemeMode(1) }, label = { Text("Light") })
                    FilterChip(selected = themeMode == 2, onClick = { viewModel.setThemeMode(2) }, label = { Text("Dark") })
                }
            }
        }

        Card {
            Column(Modifier.padding(16.dp)) {
                Text("App Theme", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppTheme.values().forEach { theme ->
                        FilterChip(
                            selected = selectedTheme == theme,
                            onClick = { viewModel.setAppTheme(theme) },
                            label = { Text(theme.name) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit, contentPadding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 16.dp)
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Spacer(Modifier.width(8.dp))
            Text("About", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Tribute",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Abdullah Bari Asif",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Dedicated with gratitude and respect for continuous contributions, inspiration, and visionary guidance to MadrasaTube.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Code,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Credits & Acknowledgments",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Original Developer: Hotaro",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Special thanks to Hotaro for founding the initial baseline of this open-source application and laying the foundational work.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    text = "MadrasaTube",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "A distraction-free, ad-free Islamic video learning app designed to facilitate focused study.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
