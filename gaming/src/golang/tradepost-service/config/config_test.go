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

package config

import (
	"bytes"
	"os"
	"regexp"
	"testing"

	"github.com/spf13/viper"
	"github.com/stretchr/testify/assert"
)

func readConfig(yml []byte) (*Config, error) {
	viper.SetConfigType("yaml")

	// Read the config
	err := viper.ReadConfig(bytes.NewBuffer(yml))
	if err != nil {
		return &Config{}, err
	}

	var c Config
	err = viper.Unmarshal(&c)
	if err != nil {
		return &Config{}, err
	}

	return &c, nil
}

func TestServerURL(t *testing.T) {
	c, err := NewConfig()
	assert.Nil(t, err)

	assert.Regexp(t, regexp.MustCompile(`^[A-Za-z0-9.]*:\d*$`), c.Server.URL())
}

func TestSpannerDB(t *testing.T) {
	cfgExample := []byte(`
server:
  host: localhost
  port: 8083
spanner:
  project_id: test-123
  instance_id: game-test-1
  database_id: game-db-1
`)

	c, err := readConfig(cfgExample)
	assert.Nil(t, err)

	assert.Regexp(t, regexp.MustCompile(`^projects/[a-z0-9-]*/instances/[a-z0-9-]*/databases/[a-z0-9-]*$`), c.Spanner.DB())
}

func TestEnvironmentVariables(t *testing.T) {
	os.Setenv("SPANNER_PROJECT_ID", "test-project")
	os.Setenv("SPANNER_INSTANCE_ID", "test-instance")
	os.Setenv("SPANNER_DATABASE_ID", "test-database")

	c, err := NewConfig()
	assert.Nil(t, err)

	assert.Equal(t, "projects/test-project/instances/test-instance/databases/test-database", c.Spanner.DB())
}
