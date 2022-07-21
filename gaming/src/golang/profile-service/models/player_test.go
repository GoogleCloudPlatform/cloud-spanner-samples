//go:build !integration

// Copyright 2022 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package models

import (
	"testing"

	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
)

func TestValidEmails(t *testing.T) {
	var tests = []string{"good@gmail.com", "good@somedomain.net", "good.email@somedomain.org"}

	for _, testEmail := range tests {
		var player = Player{Email: testEmail, Password: "testpassword", Player_name: "Test Player"}

		t.Logf("Testing '%s'", testEmail)

		err := player.Validate()

		assert.Nil(t, err)
	}
}

func TestInvalidEmails(t *testing.T) {
	var tests = []string{"bademail", "bad@gmail"}

	for _, testEmail := range tests {
		var player = Player{Email: testEmail, Password: "testpassword", Player_name: "Test Player"}

		t.Logf("Testing '%s'", testEmail)

		err := player.Validate()

		assert.NotNil(t, err)
	}
}

func TestHashPassword(t *testing.T) {
	var tests = []string{"mypass", "somepass", "som1pass"}

	for _, pass := range tests {
		hash, err := hashPassword(pass)

		assert.Nil(t, err)

		err = validatePassword(pass, hash)

		assert.Nil(t, err)

	}

}

func TestGenerateUUID(t *testing.T) {
	id := generateUUID()
	_, err := uuid.Parse(id)

	assert.Nil(t, err)

}
