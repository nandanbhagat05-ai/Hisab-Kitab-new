package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.model.UiState
import com.example.data.repository.KhataRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOut
import com.example.ui.components.BusinessProfileSheet
import com.example.ui.screens.CashbookScreen
import com.example.ui.screens.CustomerDetailScreen
import com.example.ui.screens.KhataScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.theme.KhataBookTheme
import com.example.ui.viewmodel.KhataViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

object Routes {
    const val ROUTE_KHATA = "khata"
    const val ROUTE_CASHBOOK = "cashbook"
    const val ROUTE_REPORTS = "reports"
    const val ROUTE_CUSTOMER_DETAIL = "customer/{customerId}"

    fun customerDetail(customerId: String): String = "customer/$customerId"
}

private data class MainTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val MAIN_TABS = listOf(
    MainTab(
        route = Routes.ROUTE_KHATA,
        label = "Khata",
        selectedIcon = Icons.AutoMirrored.Rounded.MenuBook,
        unselectedIcon = Icons.AutoMirrored.Outlined.MenuBook,
        testTag = "nav_tab_khata"
    ),
    MainTab(
        route = Routes.ROUTE_CASHBOOK,
        label = "Cashbook",
        selectedIcon = Icons.Rounded.AccountBalanceWallet,
        unselectedIcon = Icons.Outlined.AccountBalanceWallet,
        testTag = "nav_tab_cashbook"
    ),
    MainTab(
        route = Routes.ROUTE_REPORTS,
        label = "Reports",
        selectedIcon = Icons.Rounded.Assessment,
        unselectedIcon = Icons.Outlined.Assessment,
        testTag = "nav_tab_reports"
    )
)

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KhataBookTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            AuthenticatedKhataApp(
                currentUserId = user.uid,
                userEmail = user.email,
                defaultDisplayName = user.displayName.orEmpty()
            )
        }
    }
}

@Composable
private fun AuthenticatedKhataApp(
    currentUserId: String,
    userEmail: String?,
    defaultDisplayName: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: KhataViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                KhataViewModel(KhataRepository(db), currentUserId)
            }
        }
    )

    val dashboardState by viewModel.dashboardState.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showBusinessProfileSheet by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isTopLevelRoute = currentRoute in setOf(
        Routes.ROUTE_KHATA,
        Routes.ROUTE_CASHBOOK,
        Routes.ROUTE_REPORTS
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!useNavRail && isTopLevelRoute) {
                    NavigationBar {
                        MAIN_TABS.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (!selected) {
                                        navController.navigate(tab.route) {
                                            popUpTo(Routes.ROUTE_KHATA) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
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
                if (useNavRail && isTopLevelRoute) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        Spacer(Modifier.height(16.dp))
                        MAIN_TABS.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    if (!selected) {
                                        navController.navigate(tab.route) {
                                            popUpTo(Routes.ROUTE_KHATA) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    when (val state = dashboardState) {
                        is UiState.Loading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator()
                                    Text(
                                        text = "Loading AtharvNandan...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        is UiState.Error -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        is UiState.Success -> {
                            val data = state.data
                            val businessName = data.profile?.businessName?.ifBlank { "AtharvNandan" }
                                ?: "AtharvNandan"
                            val businessPhone = data.profile?.phone.orEmpty()

                            NavHost(
                                navController = navController,
                                startDestination = Routes.ROUTE_KHATA,
                                modifier = Modifier
                                    .widthIn(max = 840.dp)
                                    .fillMaxSize()
                            ) {
                                composable(Routes.ROUTE_KHATA) {
                                    KhataScreen(
                                        data = data,
                                        isSaving = isSaving,
                                        onCustomerClick = { customerId ->
                                            navController.navigate(Routes.customerDetail(customerId))
                                        },
                                        onAddCustomer = { name, phone, notes ->
                                            viewModel.addCustomer(name, phone, notes)
                                        },
                                        onOpenBusinessProfile = { showBusinessProfileSheet = true }
                                    )
                                }

                                composable(Routes.ROUTE_CASHBOOK) {
                                    CashbookScreen(
                                        data = data,
                                        isSaving = isSaving,
                                        onAddCashEntry = { type, amountPaise, category, mode, note, dateMillis ->
                                            viewModel.addCashEntry(type, amountPaise, category, mode, note, dateMillis)
                                        },
                                        onUpdateCashEntry = { id, type, amountPaise, category, mode, note, dateMillis ->
                                            viewModel.updateCashEntry(id, type, amountPaise, category, mode, note, dateMillis)
                                        },
                                        onDeleteCashEntry = { id ->
                                            viewModel.deleteCashEntry(id)
                                        }
                                    )
                                }

                                composable(Routes.ROUTE_REPORTS) {
                                    ReportsScreen(
                                        data = data,
                                        onCustomerClick = { customerId ->
                                            navController.navigate(Routes.customerDetail(customerId))
                                        }
                                    )
                                }

                                composable(
                                    route = Routes.ROUTE_CUSTOMER_DETAIL,
                                    arguments = listOf(navArgument("customerId") { type = NavType.StringType })
                                ) { backStackEntry ->
                                    val customerId = backStackEntry.arguments?.getString("customerId").orEmpty()
                                    val customerWithBalance = data.customersWithBalance
                                        .firstOrNull { it.customer.id == customerId }
                                    val timeline = remember(data.allLedgerEntries, customerId) {
                                        viewModel.computeCustomerLedgerTimeline(data.allLedgerEntries, customerId)
                                    }

                                    CustomerDetailScreen(
                                        customerWithBalance = customerWithBalance,
                                        timeline = timeline,
                                        businessName = businessName,
                                        businessPhone = businessPhone,
                                        isSaving = isSaving,
                                        onBack = { navController.popBackStack() },
                                        onUpdateCustomer = { name, phone, notes ->
                                            viewModel.updateCustomer(customerId, name, phone, notes)
                                        },
                                        onDeleteCustomer = {
                                            viewModel.deleteCustomer(customerId) {
                                                navController.popBackStack()
                                            }
                                        },
                                        onAddEntry = { type, amountPaise, note, dateMillis, alsoCashbook ->
                                            viewModel.addLedgerEntry(
                                                customerId = customerId,
                                                type = type,
                                                amountPaise = amountPaise,
                                                note = note,
                                                entryDateMillis = dateMillis,
                                                alsoRecordInCashbook = alsoCashbook,
                                                customerName = customerWithBalance?.customer?.name.orEmpty()
                                            )
                                        },
                                        onUpdateEntry = { entryId, type, amountPaise, note, dateMillis ->
                                            viewModel.updateLedgerEntry(
                                                entryId = entryId,
                                                type = type,
                                                amountPaise = amountPaise,
                                                note = note,
                                                entryDateMillis = dateMillis
                                            )
                                        },
                                        onDeleteEntry = { entryId ->
                                            viewModel.deleteLedgerEntry(entryId)
                                        }
                                    )
                                }
                            }

                            if (showBusinessProfileSheet) {
                                BusinessProfileSheet(
                                    profile = data.profile ?: com.example.data.model.UserProfile(
                                        userId = currentUserId,
                                        businessName = "AtharvNandan",
                                        ownerName = defaultDisplayName
                                    ),
                                    userEmail = userEmail,
                                    isSaving = isSaving,
                                    onSave = { bName, oName, phone ->
                                        viewModel.saveUserProfile(bName, oName, phone) {
                                            showBusinessProfileSheet = false
                                        }
                                    },
                                    onSignOut = {
                                        showBusinessProfileSheet = false
                                        signOut(credentialManager, scope)
                                    },
                                    onDismiss = { showBusinessProfileSheet = false }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
