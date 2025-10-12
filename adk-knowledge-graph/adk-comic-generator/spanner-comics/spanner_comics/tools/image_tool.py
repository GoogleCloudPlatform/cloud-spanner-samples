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

import base64
import mimetypes
import os
import uuid
from google import genai
from google.genai import types


def save_binary_file(file_name, data):
    f = open(file_name, "wb")
    f.write(data)
    f.close()
    print(f"File saved to to: {file_name}")


def generate_image(prompt: str) -> str:
    """Generates an image based on a prompt."""

    if not prompt:
        prompt = f"Create a comic strip about the best features in Spanner"
    prompt = f"A comic strip about a developer trying to find the answer to the question: '{prompt}'"

    try:
        client = genai.Client(
            api_key=os.environ.get("GOOGLE_API_KEY"),
        )

        model = "gemini-2.5-flash-image-preview"
        contents = [
            types.Content(
                role="user",
                parts=[
                    types.Part.from_text(text=prompt),
                ],
            ),
        ]
        generate_content_config = types.GenerateContentConfig(
            response_modalities=[
                "IMAGE",
            ],
        )

        image_data = b""
        mime_type = ""
        for chunk in client.models.generate_content_stream(
            model=model,
            contents=contents,
            config=generate_content_config,
        ):
            if (
                chunk.candidates is None
                or chunk.candidates[0].content is None
                or chunk.candidates[0].content.parts is None
            ):
                continue
            if chunk.candidates[0].content.parts[0].inline_data and chunk.candidates[0].content.parts[0].inline_data.data:
                part = chunk.candidates[0].content.parts[0]
                image_data += part.inline_data.data
                if not mime_type:
                    mime_type = part.inline_data.mime_type

        if image_data:
            file_extension = mimetypes.guess_extension(mime_type) or ".png"
            file_name = f"{uuid.uuid4()}{file_extension}"
            save_binary_file(file_name, image_data)
            return os.path.abspath(file_name)
        else:
            return "Image generation failed to produce image data."

    except Exception as e:
        print(f"Error generating image: {e}")
        return "Image generation failed."


if __name__ == "__main__":

    # Example usage
    prompt = "What are regions in Spanner?"
    image_path = generate_image(prompt)
    print(f"Generated image path: {image_path}")