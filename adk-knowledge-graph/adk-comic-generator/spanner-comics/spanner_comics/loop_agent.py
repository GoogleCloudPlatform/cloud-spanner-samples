# Copyright 2025 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.


from google.adk.agents import LlmAgent, SequentialAgent, LoopAgent
from .tools.image_tool import generate_image

# --- 1. Define the sub-agents ---
image_generation_prompt_agent = LlmAgent(
    name="image_generation_prompt_agent",
    model="gemini-2.5-pro",
    description="Creates a prompt for image generation.",
    instruction="You are an image generation prompt creator. You will be given a story. You will create a prompt for image generation based on the story and output a JSON object with 'prompt' and 'story' keys. The image needs to be a comic strip. Make it engaging.",
)

image_generation_agent = LlmAgent(
    name="image_generation_agent",
    model="gemini-2.5-pro",
    description="Generates images based on a prompt.",
    instruction="You are an image generation agent. You will be given a JSON object with 'prompt' and 'story' keys. You will generate an engaging and funny comic based on the prompt and output a JSON object with 'file_path' and 'story' keys.",
    tools=[generate_image],
)

scoring_and_checking_agent = LlmAgent(
    name="scoring_and_checking_agent",
    model="gemini-2.5-pro",
    description="Scores and checks the quality of an image.",
    instruction="You are an image quality checker. You will be given a JSON object with 'file_path' and 'story' keys. You will score the image on a scale of 1 to 10, where 10 is best. You will also check for typos in the image. If the score is 8 or higher and there are 3 or fewer typos, you will output a JSON object with 'status': 'success' and 'file_path': the file path from the input. Otherwise, you will output a JSON object with 'status': 'failure' and 'story': the story from the input. Your output must be a JSON object.",
)

# --- 2. Define the Sequential Agent ---
image_generation_scoring_agent = SequentialAgent(
    name="image_generation_scoring_agent",
    description=(
        """
        Analyzes a input text and creates the image generation prompt, generates the relevant images with imagen3 and scores the images."
        1. Invoke the image_generation_prompt_agent agent to generate the prompt for generating images
        2. Invoke the image_generation_agent agent to generate the images
        3. Invoke the scoring_and_checking_agent agent to score and check the images
        """
    ),
    sub_agents=[image_generation_prompt_agent, image_generation_agent, scoring_and_checking_agent],
)

# --- 3. Define the Loop Agent ---
image_scoring = LoopAgent(
    name="image_scoring",
    description="Repeatedly runs a sequential process and checks a termination condition.",
    sub_agents=[
        image_generation_scoring_agent,
    ],
    max_iterations=3,
)
