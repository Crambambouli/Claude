package com.puzzle.android

import com.puzzle.android.data.db.ExampleEntity
import com.puzzle.android.data.model.HealthResponse
import com.puzzle.android.data.repository.ExampleRepository
import com.puzzle.android.viewmodel.HealthStatus
import com.puzzle.android.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: ExampleRepository
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        // Stub the examples Flow so StateFlow initialisation doesn't hang
        repository = mock()
        whenever(repository.examples).thenReturn(flowOf(emptyList()))

        viewModel = MainViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Initial state ──────────────────────────────────────────────────────────

    @Test
    fun `initial health status is Idle`() {
        assertEquals(HealthStatus.Idle, viewModel.uiState.value.healthStatus)
    }

    @Test
    fun `initial examples list is empty`() {
        assertTrue(viewModel.examples.value.isEmpty())
    }

    // ── checkHealth – success ─────────────────────────────────────────────────

    @Test
    fun `checkHealth transitions to Success when repository returns ok`() = runTest {
        val healthResponse = HealthResponse(status = "ok", version = "0.1.0")
        whenever(repository.fetchHealth()).thenReturn(Result.success(healthResponse))

        viewModel.checkHealth()
        advanceUntilIdle()

        val status = viewModel.uiState.value.healthStatus
        assertTrue("Expected Success but got $status", status is HealthStatus.Success)
        assertEquals("ok", (status as HealthStatus.Success).statusText)
        assertEquals("0.1.0", status.version)
    }

    @Test
    fun `checkHealth sets Loading state before result arrives`() = runTest {
        // fetchHealth suspends until the test dispatcher advances
        whenever(repository.fetchHealth()).thenReturn(Result.success(HealthResponse("ok")))

        viewModel.checkHealth()

        // Before advancing: should be Loading
        assertEquals(HealthStatus.Loading, viewModel.uiState.value.healthStatus)

        advanceUntilIdle()
    }

    // ── checkHealth – failure ─────────────────────────────────────────────────

    @Test
    fun `checkHealth transitions to Error when repository fails`() = runTest {
        val errorMsg = "Connection refused"
        whenever(repository.fetchHealth()).thenReturn(Result.failure(RuntimeException(errorMsg)))

        viewModel.checkHealth()
        advanceUntilIdle()

        val status = viewModel.uiState.value.healthStatus
        assertTrue("Expected Error but got $status", status is HealthStatus.Error)
        assertEquals(errorMsg, (status as HealthStatus.Error).message)
    }

    // ── checkHealth – no duplicate calls ─────────────────────────────────────

    @Test
    fun `checkHealth is ignored when already Loading`() = runTest {
        whenever(repository.fetchHealth()).thenReturn(Result.success(HealthResponse("ok")))

        viewModel.checkHealth() // sets Loading
        viewModel.checkHealth() // should be ignored
        advanceUntilIdle()

        // fetchHealth must only be called once
        verify(repository, org.mockito.kotlin.times(1)).fetchHealth()
    }

    // ── saveExample ───────────────────────────────────────────────────────────

    @Test
    fun `saveExample delegates to repository`() = runTest {
        val id = "test-id-123"
        viewModel.saveExample(id)
        advanceUntilIdle()

        verify(repository).saveExample(id)
    }

    // ── clearExamples ─────────────────────────────────────────────────────────

    @Test
    fun `clearExamples delegates to repository`() = runTest {
        viewModel.clearExamples()
        advanceUntilIdle()

        verify(repository).clearExamples()
    }

    // ── examples Flow ─────────────────────────────────────────────────────────

    @Test
    fun `examples StateFlow reflects repository Flow`() = runTest {
        val entities = listOf(
            ExampleEntity(id = "a"),
            ExampleEntity(id = "b")
        )
        whenever(repository.examples).thenReturn(flowOf(entities))

        // Re-create ViewModel so it subscribes with the updated stub
        val vm = MainViewModel(repository)
        advanceUntilIdle()

        assertEquals(entities, vm.examples.value)
    }
}
