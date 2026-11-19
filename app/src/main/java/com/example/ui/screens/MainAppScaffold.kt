package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Api
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import com.example.data.model.LookupUiState
import com.example.data.model.UiState
import com.example.security.signOutGoogle
import com.example.ui.viewmodel.ConsultaViewModel
import com.google.firebase.auth.FirebaseUser

private const val ROUTE_SEARCH = "search"
private const val ROUTE_HISTORY = "history"
private const val ROUTE_APIS = "apis"
private const val ROUTE_DOSSIER = "dossier/{consultaId}"

private data class TopLevelTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val topLevelTabs = listOf(
    TopLevelTab(ROUTE_SEARCH, "Consultar", Icons.Filled.Search, Icons.Outlined.Search),
    TopLevelTab(ROUTE_HISTORY, "Histórico", Icons.Filled.History, Icons.Outlined.History),
    TopLevelTab(ROUTE_APIS, "APIs BR", Icons.Filled.Api, Icons.Outlined.Api)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScaffold(
    user: FirebaseUser,
    viewModel: ConsultaViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val consultasState by viewModel.consultasState.collectAsStateWithLifecycle()
    val lookupState by viewModel.lookupState.collectAsStateWithLifecycle()
    val thinkingState by viewModel.thinkingState.collectAsStateWithLifecycle()
    val cnpjState by viewModel.cnpjToolState.collectAsStateWithLifecycle()
    val cepState by viewModel.cepToolState.collectAsStateWithLifecycle()
    val dddState by viewModel.dddToolState.collectAsStateWithLifecycle()
    val ibgeState by viewModel.ibgeToolState.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()

    var showSignOutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(user.uid) {
        viewModel.syncUserProfile(user)
    }

    LaunchedEffect(actionMessage) {
        val msg = actionMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearActionMessage()
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isDetailRoute = currentRoute?.startsWith("dossier/") == true

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Sair da conta Google?") },
            text = {
                Text("Conectado como ${user.email ?: user.displayName ?: "usuário Google"}. Seu histórico permanece salvo com segurança no Cloud Firestore.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutDialog = false
                        signOutGoogle(credentialManager, scope)
                    },
                    modifier = Modifier.testTag("confirm_sign_out_button")
                ) {
                    Text("Sair")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            contentWindowInsets = WindowInsets.navigationBars,
            topBar = {
                if (!isDetailRoute) {
                    TopAppBar(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (user.photoUrl != null) {
                                    AsyncImage(
                                        model = user.photoUrl,
                                        contentDescription = "Foto da conta Google",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.AccountCircle,
                                        contentDescription = "Conta Google",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Text(
                                    text = "Consulta Pública",
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { showSignOutDialog = true },
                                modifier = Modifier.testTag("sign_out_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = "Sair da conta Google"
                                )
                            }
                        }
                    )
                }
            },
            bottomBar = {
                if (!useRail && !isDetailRoute) {
                    NavigationBar {
                        topLevelTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(ROUTE_SEARCH) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("nav_tab_${tab.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail && !isDetailRoute) {
                    NavigationRail {
                        Spacer(modifier = Modifier.weight(0.1f))
                        topLevelTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(ROUTE_SEARCH) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("rail_tab_${tab.route}")
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_SEARCH
                    ) {
                        composable(ROUTE_SEARCH) {
                            SearchScreen(
                                lookupState = lookupState,
                                userDisplayName = user.displayName ?: user.email ?: "Cidadão",
                                onExecuteSearch = { mode, name, cpf, phone ->
                                    viewModel.executeConsulta(
                                        mode = mode,
                                        nameInput = name,
                                        cpfInput = cpf,
                                        phoneInput = phone,
                                        onSavedNavigate = { newId ->
                                            viewModel.resetThinkingState()
                                            navController.navigate("dossier/$newId")
                                        }
                                    )
                                },
                                onClearError = {
                                    if (lookupState is LookupUiState.Error) {
                                        viewModel.resetLookupState()
                                    }
                                }
                            )
                        }

                        composable(ROUTE_HISTORY) {
                            HistoryScreen(
                                consultasState = consultasState,
                                onOpenConsulta = { record ->
                                    viewModel.resetThinkingState()
                                    navController.navigate("dossier/${record.id}")
                                },
                                onTogglePin = { record ->
                                    viewModel.togglePinned(record)
                                },
                                onDeleteConsulta = { id ->
                                    viewModel.deleteConsulta(id)
                                }
                            )
                        }

                        composable(ROUTE_APIS) {
                            PublicApisScreen(
                                cnpjState = cnpjState,
                                cepState = cepState,
                                dddState = dddState,
                                ibgeState = ibgeState,
                                onLookupCnpj = viewModel::queryCnpj,
                                onLookupCep = viewModel::queryCep,
                                onLookupDdd = viewModel::queryDdd,
                                onLookupIbge = viewModel::queryIbgeName
                            )
                        }

                        composable(
                            route = ROUTE_DOSSIER,
                            arguments = listOf(navArgument("consultaId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val consultaId = backStackEntry.arguments?.getString("consultaId").orEmpty()
                            val fromList = (consultasState as? UiState.Success)?.data
                                ?.firstOrNull { it.id == consultaId }
                            val fromLookup = (lookupState as? LookupUiState.Success)?.record
                                ?.takeIf { it.id == consultaId }
                            val record = fromList ?: fromLookup

                            if (record != null) {
                                DossierDetailScreen(
                                    record = record,
                                    thinkingState = thinkingState,
                                    onBack = { navController.popBackStack() },
                                    onTogglePin = { viewModel.togglePinned(record) },
                                    onRunDeepThinking = { viewModel.runDeepThinking(record) },
                                    onDelete = {
                                        viewModel.deleteConsulta(record.id) {
                                            navController.popBackStack()
                                        }
                                    }
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Carregando dossiê...",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
