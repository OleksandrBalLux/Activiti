/*
 * Copyright 2010-2020 Alfresco Software, Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.activiti.spring.boot.tasks;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.activiti.api.model.shared.model.VariableInstance;
import org.activiti.api.process.model.ProcessInstance;
import org.activiti.api.task.model.Task;
import org.activiti.common.util.DateFormatterProvider;
import org.activiti.spring.boot.process.ProcessBaseRuntime;
import org.activiti.spring.boot.security.util.SecurityUtil;
import org.activiti.spring.boot.test.util.ProcessCleanUpUtil;
import org.activiti.spring.boot.test.util.TaskCleanUpUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class TaskRuntimeVariableMappingIT {

    private static final String TASK_REGULAR_MAPPING = "taskVariableMapping";

    private static final String TASK_MAP_ALL = "taskVariableMappingSendAll";

    private static final String TASK_NO_MAPPING = "taskVariableMappingSendNone";

    private static final String TASK_MAP_ALL_INPUTS = "taskVariableMappingSendAllInputs";

    private static final String TASK_MAP_ALL_OUTPUTS = "taskVariableMappingSendAllOutputs";

    private static final String TASK_MAP_ALL_PREVALENCE = "taskVariableMappingSendAllPrevalence";

    @Autowired
    private SecurityUtil securityUtil;

    @Autowired
    private TaskBaseRuntime taskBaseRuntime;

    @Autowired
    private ProcessBaseRuntime processBaseRuntime;

    @Autowired
    private TaskCleanUpUtil taskCleanUpUtil;

    @Autowired
    private ProcessCleanUpUtil processCleanUpUtil;

    @Autowired
    private DateFormatterProvider dateFormatterProvider;

    @AfterEach
    public void cleanUp() {
        processCleanUpUtil.cleanUpWithAdmin();
        taskCleanUpUtil.cleanUpWithAdmin();
    }

    @Test
    public void should_mapTaskVariables_when_thereIsMapping() {
        ProcessInstance processInstance = processBaseRuntime.startProcessWithProcessDefinitionKey(TASK_REGULAR_MAPPING);

        Date date = dateFormatterProvider.parse("2019-09-01");
        Date datetime = dateFormatterProvider.parse("2019-09-01T10:20:30.000Z");

        Task task = checkTasks(processInstance.getId());

        assertThat(task.getName()).isEqualTo("testSimpleTask");

        List<VariableInstance> procVariables = processBaseRuntime.getProcessVariablesByProcessId(processInstance.getId());
        assertThat(procVariables)
                .isNotNull()
                .extracting(VariableInstance::getName,
                            VariableInstance::getType,
                            VariableInstance::getValue)
                .containsOnly(tuple("process_variable_unmapped_1",
                                    "string",
                                    "unmapped1Value"),
                              tuple("process_variable_inputmap_1",
                                    "string",
                                    "inputmap1Value"),
                              tuple("process_variable_outputmap_1",
                                    "string",
                                    "outputmap1Value"),
                              tuple("process-variable-date",
                                    "date",
                                    date),
                              tuple("process-variable-datetime",
                                    "date",
                                    datetime)
                              );

        List<VariableInstance> taskVariables = taskBaseRuntime.getTasksVariablesByTaskId(task.getId());

        assertThat(taskVariables)
                .isNotNull()
                .extracting(VariableInstance::getName,
                            VariableInstance::getType,
                            VariableInstance::getValue)
                .containsOnly(tuple("task_input_variable_name_1",
                                    "string",
                                    "inputmap1Value"),
                              tuple("task-variable-date",
                                    "date",
                                    date),
                              tuple("task-variable-datetime",
                                    "date",
                                    datetime)
                              );

        Map<String, Object> variables = new HashMap<>();
        variables.put("task_input_variable_name_1",
                      "outputValue"); //This should not be set to 'process_variable_inputmap_1'
        variables.put("task_output_variable_name_1",
                      "outputTaskValue"); //This should be set to 'process_variable_outputmap_1'

        taskBaseRuntime.completeTask(task.getId(), variables);

        procVariables = processBaseRuntime.getProcessVariablesByProcessId(processInstance.getId());
        assertThat(procVariables)
                .isNotNull()
                .extracting(VariableInstance::getName,
                            VariableInstance::getValue)
                .containsOnly(
                        tuple("process_variable_unmapped_1",
                              "unmapped1Value"),
                        tuple("process_variable_inputmap_1",
                              "inputmap1Value"),
                        tuple("process_variable_outputmap_1",
                              "outputTaskValue"),
                        tuple("process-variable-date",
                              date),
                        tuple("process-variable-datetime",
                              datetime)

                );
        processBaseRuntime.delete(processInstance.getId());
    }

    @Test
    public void should_notMapTaskVariables_when_thereIsNoMapping() {
        ProcessInstance processInstance = processBaseRuntime.startProcessWithProcessDefinitionKey(TASK_NO_MAPPING);

        List<Task> tasks = taskBaseRuntime.getTasksByProcessInstanceId(processInstance.getId());
        assertThat(tasks).hasSize(1);

        Task task = tasks.get(0);
        assertThat(task.getName()).isEqualTo("testSimpleTask");

        List<VariableInstance> procVariables = processBaseRuntime.getProcessVariablesByProcessId(processInstance.getId());

        assertThat(procVariables)
            .isNotNull()
            .extracting(VariableInstance::getName,
                VariableInstance::getValue)
            .containsOnly(tuple("process_variable_unmapped_1",
                "unmapped1Value"),
                tuple("process_variable_inputmap_1",
                    "inputmap1Value"),
                tuple("process_variable_outputmap_1",
                    "outputmap1Value"));

        List<VariableInstance> taskVariables = taskBaseRuntime.getTasksVariablesByTaskId(task.getId());

        assertThat(taskVariables)
            .isEmpty();

        Map<String, Object> variables = new HashMap<>();
        variables.put("task_input_variable_name_1",
            "outputValue");
        variables.put("task_output_variable_name_1",
            "outputTaskValue");

        taskBaseRuntime.completeTask(task.getId(), variables);

        assertThat(procVariables)
            .isNotNull()
            .extracting(VariableInstance::getName,
                VariableInstance::getValue)
            .containsOnly(tuple("process_variable_unmapped_1",
                "unmapped1Value"),
                tuple("process_variable_inputmap_1",
                    "inputmap1Value"),
                tuple("process_variable_outputmap_1",
                    "outputmap1Value"));

    }

    @Test
    public void should_sendAllTaskVariables_when_thereIsMappingTypeMAP_ALL() {
        ProcessInstance processInstance = processBaseRuntime.startProcessWithProcessDefinitionKey(TASK_MAP_ALL);

        Task task = checkTasks(processInstance.getId());

        assertThat(task.getName()).isEqualTo("testSimpleTask");

        List<VariableInstance> procVariables = processBaseRuntime.getProcessVariablesByProcessId(processInstance.getId());

        assertThat(procVariables)
                .isNotNull()
                .extracting(VariableInstance::getName,
                            VariableInstance::getValue)
                .containsOnly(tuple("process_variable_unmapped_1",
                                    "unmapped1Value"),
                              tuple("process_variable_inputmap_1",
                                    "inputmap1Value"),
                              tuple("process_variable_outputmap_1",
                                    "outputmap1Value"));

        List<VariableInstance> taskVariables = taskBaseRuntime.getTasksVariablesByTaskId(task.getId());

        assertThat(taskVariables)
                .isNotNull()
                .extracting(VariableInstance::getName,
                            VariableInstance::getValue)
                .containsOnly(tuple("process_variable_unmapped_1",
                                    "unmapped1Value"),
                              tuple("process_variable_inputmap_1",
                                    "inputmap1Value"),
                              tuple("process_variable_outputmap_1",
                                    "outputmap1Value"));

        Map<String, Object> variables = new HashMap<>();
        variables.put("task_input_variable_name_1",
                      "outputValue");
        variables.put("task_output_variable_name_1",
                      "outputTaskValue");

        taskBaseRuntime.completeTask(task.getId(), variables);

        procVariables = processBaseRuntime.getProcessVariablesByProcessId(processInstance.getId());
        assertThat(procVariables)
                .isNotNull()
                .extracting(VariableInstance::getName,
                            VariableInstance::getValue)
                .containsExactlyInAnyOrder(
                        tuple("process_variable_unmapped_1",
                              "unmapped1Value"),
                        tuple("process_variable_inputmap_1",
                              "inputmap1Value"),
                        tuple("process_variable_outputmap_1",
                              "outputmap1Value"),
                        tuple("task_input_variable_name_1",
                              "outputValue"),
                        tuple("task_output_variable_name_1",
                              "outputTaskValue")
                        //since there is no mapping for outputs either, all the variables are passed
                );
        processBaseRuntime.delete(processInstance.getId());
    }

    @Test
    public void should_sendAllInputTasksVariablesAndOnlySelectedOuputs_when_thereIsMappingTypeMAP_ALL_INPUTS() {

        ProcessInstance processInstance = processBaseRuntime.startProcessWithProcessDefinitionKey(TASK_MAP_ALL_INPUTS);

        Task task = checkTasks(processInstance.getId());

        assertThat(task.getName()).isEqualTo("testSimpleTask");

        List<VariableInstance> procVariables = processBaseRuntime.getProcessVariablesByProcessId(processInstance.getId());

        assertThat(procVariables)
            .isNotNull()
            .extracting(VariableInstance::getName,
                VariableInstance::getValue)
            .containsOnly(tuple("process_variable_unmapped_1",
                "unmapped1Value"),
                tuple("process_variable_inputmap_1",
                    "inputmap1Value"),
                tuple("process_variable_outputmap_1",
                    "outputmap1Value"));

        List<VariableInstance> taskVariables = taskBaseRuntime.getTasksVariablesByTaskId(task.getId());

        assertThat(taskVariables)
            .isNotNull()
            .extracting(VariableInstance::getName,
                VariableInstance::getValue)
            .containsOnly(tuple("process_variable_unmapped_1",
                "unmapped1Value"),
                tuple("process_variable_inputmap_1",
                    "inputmap1Value"),
                tuple("process_variable_outputmap_1",
                    "outputmap1Value"));

        Map<String, Object> variables = new HashMap<>();
        variables.put("task_input_variable_name_1",
            "notMappedValue");
        variables.put("task_output_variable_name_1",
            "outputMappedValue");

        taskBaseRuntime.completeTask(task.getId(), variables);

        procVariables = processBaseRuntime.getProcessVariablesByProcessId(processInstance.getId());

        assertThat(procVariables)
            .isNotNull()
            .extracting(VariableInstance::getName,
                VariableInstance::getValue)
            .containsOnly(tuple("process_variable_unmapped_1",
                "unmapped1Value"),
                tuple("process_variable_inputmap_1",
                    "inputmap1Value"),
                tuple("process_variable_outputmap_1",
                    "outputMappedValue"));

        processBaseRuntime.delete(processInstance.getId());

    }

    @Test
    public void should_sendAllOutputTaskVariablesAndOnlySelectedInputs_when_thereIsMappingTypeMAP_ALL_OUTPUTS() {

        ProcessInstance processInstance = processBaseRuntime.startProcessWithProcessDefinitionKey(TASK_MAP_ALL_OUTPUTS);

        List<Task> tasks = taskBaseRuntime.getTasksByProcessInstanceId(processInstance.getId());
        assertThat(tasks).isNotEmpty();
        assertThat(tasks).hasSize(1);

        Task task = tasks.get(0);
        assertThat(task.getName()).isEqualTo("testSimpleTask");

        List<VariableInstance> procVariables = processBaseRuntime.getProcessVariablesByProcessId(processInstance.getId());

        assertThat(procVariables)
            .isNotNull()
            .extracting(VariableInstance::getName,
                VariableInstance::getValue)
            .containsOnly(tuple("process_variable_unmapped_1",
                "unmapped1Value"),
                tuple("process_variable_inputmap_1",
                    "inputmap1Value"),
                tuple("process_variable_outputmap_1",
                    "outputmap1Value"));

        List<VariableInstance> taskVariables = taskBaseRuntime.getTasksVariablesByTaskId(task.getId());

        assertThat(taskVariables)
            .isNotNull()
            .extracting(VariableInstance::getName,
                VariableInstance::getType,
                VariableInstance::getValue)
            .containsOnly(tuple("task_input_variable_name_1",
                "string",
                "inputmap1Value"));

        Map<String, Object> variables = new HashMap<>();
        variables.put("process_variable_outputmap_1",
            "outputMappedValue");
        variables.put("new_task_output_variable_name",
            "newOutputMappedValue");

        taskBaseRuntime.completeTask(task.getId(), variables);

        procVariables = processBaseRuntime.getProcessVariablesByProcessId(processInstance.getId());

        assertThat(procVariables)
            .isNotNull()
            .extracting(VariableInstance::getName,
                VariableInstance::getValue)
            .containsOnly(tuple("process_variable_unmapped_1",
                "unmapped1Value"),
                tuple("process_variable_inputmap_1",
                    "inputmap1Value"),
                tuple("process_variable_outputmap_1",
                    "outputMappedValue"),
                tuple("new_task_output_variable_name",
                "newOutputMappedValue"),
                tuple("task_input_variable_name_1",
                    "inputmap1Value"));

        processBaseRuntime.delete(processInstance.getId());

    }

    @Test
    public void should_mappingTypeHavePrevalenceOverExplicitMapping() {

        ProcessInstance processInstance = processBaseRuntime.startProcessWithProcessDefinitionKey(TASK_MAP_ALL_PREVALENCE);

        Task task = checkTasks(processInstance.getId());

        assertThat(task.getName()).isEqualTo("testSimpleTask");

        List<VariableInstance> procVariables = processBaseRuntime.getProcessVariablesByProcessId(processInstance.getId());

        assertThat(procVariables)
            .isNotNull()
            .extracting(VariableInstance::getName,
                VariableInstance::getValue)
            .containsOnly(tuple("process_variable_unmapped_1",
                "unmapped1Value"),
                tuple("process_variable_inputmap_1",
                    "inputmap1Value"),
                tuple("process_variable_outputmap_1",
                    "outputmap1Value"));

        List<VariableInstance> taskVariables = taskBaseRuntime.getTasksVariablesByTaskId(task.getId());

        assertThat(taskVariables)
            .isNotNull()
            .extracting(VariableInstance::getName,
                VariableInstance::getValue)
            .containsOnly(tuple("process_variable_unmapped_1",
                "unmapped1Value"),
                tuple("process_variable_inputmap_1",
                    "inputmap1Value"),
                tuple("process_variable_outputmap_1",
                    "outputmap1Value"));

        Map<String, Object> variables = new HashMap<>();
        variables.put("task_input_variable_name_1",
            "outputValue");
        variables.put("task_output_variable_name_1",
            "outputTaskValue");

        taskBaseRuntime.completeTask(task.getId(), variables);

        procVariables = processBaseRuntime.getProcessVariablesByProcessId(processInstance.getId());
        assertThat(procVariables)
            .isNotNull()
            .extracting(VariableInstance::getName,
                VariableInstance::getValue)
            .containsExactlyInAnyOrder(
                tuple("process_variable_unmapped_1",
                    "unmapped1Value"),
                tuple("process_variable_inputmap_1",
                    "inputmap1Value"),
                tuple("process_variable_outputmap_1",
                    "outputmap1Value"),
                tuple("task_input_variable_name_1",
                    "outputValue"),
                tuple("task_output_variable_name_1",
                    "outputTaskValue")
                //since there is no mapping for outputs either, all the variables are passed
            );
        processBaseRuntime.delete(processInstance.getId());

    }

    private Task checkTasks(String processInstanceId) {
        List<Task> tasks = taskBaseRuntime.getTasksByProcessInstanceId(processInstanceId);
        assertThat(tasks).isNotEmpty();
        assertThat(tasks).hasSize(1);
        return tasks.get(0);
    }

    @Test
    public void should_supportVariableMappingAfterLoopingBack() {
        //given
        final ProcessInstance processInstance = processBaseRuntime.startProcessWithProcessDefinitionKey("Process_N4qkN051N");
        List<Task> tasks = taskBaseRuntime.getTasksByProcessInstanceId(processInstance.getId());
        assertThat(tasks)
            .extracting(Task::getName)
            .containsExactly("Enter values");

        //when the task completes with a variable value causing a loop back
        taskBaseRuntime.completeTask(tasks.get(0), singletonMap("formInput", "provided-it1"));

        //then process loops back to the first task
        waitForTaskOnProcessInstance(processInstance, "Enter values");
        List<VariableInstance> variables = processBaseRuntime.getVariables(processInstance);
        assertThat(variables)
            .extracting(
                VariableInstance::getName,
                VariableInstance::getValue
            ).containsExactly(
                tuple("providedValue", "provided-it1")
            );

        //when the task completes with a variable value not causing a loop back
        tasks = taskBaseRuntime.getTasksByProcessInstanceId(
            processInstance.getId());
        taskBaseRuntime.completeTask(tasks.get(0),
            singletonMap("formInput", "go"));

        //then the process reaches the next task
        waitForTaskOnProcessInstance(processInstance, "Wait");
        variables = processBaseRuntime.getVariables(processInstance);
        assertThat(variables)
            .extracting(
                VariableInstance::getName,
                VariableInstance::getValue
            ).containsExactly(
                tuple("providedValue", "go")
            );

    }

    private void waitForTaskOnProcessInstance(ProcessInstance processInstance, String taskName) {
        await().untilAsserted(() -> {
            securityUtil.logInAs("user");
            assertThat(taskBaseRuntime.getTasksByProcessInstanceId(processInstance.getId()))
                .extracting(Task::getName)
                .containsExactly(taskName);
        });
    }

    @Test
    public void mapping_should_workProperlyAfterChainingUserAndServiceTasks() {
        //given
        final ProcessInstance processInstance = processBaseRuntime.startProcessWithProcessDefinitionKey("Process_at2zjUes");
        List<Task> tasks = taskBaseRuntime.getTasksByProcessInstanceId(processInstance.getId());
        assertThat(tasks)
            .extracting(Task::getName)
            .containsExactly("Input Task");

        //when
        taskBaseRuntime.completeTask(tasks.get(0), singletonMap("inputText", "From input task"));

        //then the process has executed the service task as well and reached the next user task
        waitForTaskOnProcessInstance(processInstance, "Output Task");

        //the process variable is updated with the output of the service task
        List<VariableInstance> variables = processBaseRuntime.getVariables(processInstance);
        assertThat(variables)
            .extracting(
                VariableInstance::getName,
                VariableInstance::getValue
            ).containsExactly(
                tuple("stringVar", "From output connector")
            );

        //the task variables are updated based on the input mapping
        tasks = taskBaseRuntime.getTasksByProcessInstanceId(processInstance.getId());
        final List<VariableInstance> taskVariables = taskBaseRuntime.getTasksVariablesByTaskId(
            tasks.get(0).getId());
        assertThat(taskVariables)
            .extracting(VariableInstance::getName, VariableInstance::getValue)
            .containsExactly(tuple("outputText", "From output connector"));

    }

    @Test
    public void mapping_should_workProperlyAfterChainingUserAndCallActivities() {
        //given
        final ProcessInstance processInstance = processBaseRuntime.startProcessWithProcessDefinitionKey("Process_1wyjgrj");
        List<Task> tasks = taskBaseRuntime.getTasksByProcessInstanceId(processInstance.getId());
        assertThat(tasks)
            .extracting(Task::getName)
            .containsExactly("Input Task");

        //when
        taskBaseRuntime.completeTask(tasks.get(0), singletonMap("inputText", "From input task"));

        //then the process has executed the call activity as well and reached the next user task
        waitForTaskOnProcessInstance(processInstance, "Output Task");

        //the process variable is updated with the output of the call activity
        List<VariableInstance> variables = processBaseRuntime.getVariables(processInstance);
        assertThat(variables)
            .extracting(
                VariableInstance::getName,
                VariableInstance::getValue
            ).containsExactly(
                tuple("stringVar", "From child")
            );

        //the task variables are updated based on the input mapping
        tasks = taskBaseRuntime.getTasksByProcessInstanceId(processInstance.getId());
        final List<VariableInstance> taskVariables = taskBaseRuntime.getTasksVariablesByTaskId(
            tasks.get(0).getId());
        assertThat(taskVariables)
            .extracting(VariableInstance::getName, VariableInstance::getValue)
            .containsExactly(tuple("outputText", "From child"));

    }
}
