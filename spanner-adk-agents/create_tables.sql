-- Copyright 2026 Google LLC
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.

-- 1. Create the independent Providers table
CREATE TABLE Providers (
    ProviderId INT64 NOT NULL,
    ProviderName STRING(MAX),
    Specialty STRING(MAX)
) PRIMARY KEY (ProviderId);

-- 2. Create the parent Patients table
CREATE TABLE Patients (
    PatientId INT64 NOT NULL,
    FirstName STRING(MAX),
    LastName STRING(MAX),
    DateOfBirth DATE
) PRIMARY KEY (PatientId);

-- 3. Create the interleaved Appointments table
CREATE TABLE Appointments (
    PatientId INT64 NOT NULL,
    AppointmentId INT64 NOT NULL,
    ProviderId INT64,
    AppointmentDate DATE,
    Status STRING(MAX),
    DoctorNotes STRING(MAX),
    DoctorNotesEmbedding ARRAY<FLOAT32>(vector_length=>768)
) PRIMARY KEY (PatientId, AppointmentId),
  INTERLEAVE IN PARENT Patients ON DELETE CASCADE;

-- 4. Create the interleaved Prescriptions table
CREATE TABLE Prescriptions (
    PatientId INT64 NOT NULL,
    PrescriptionId INT64 NOT NULL,
    ProviderId INT64 NOT NULL,
    MedicationName STRING(MAX),
    DatePrescribed DATE
) PRIMARY KEY (PatientId, PrescriptionId),
  INTERLEAVE IN PARENT Patients ON DELETE CASCADE;

-- 5. Insert data into each table
INSERT INTO Providers (ProviderId, ProviderName, Specialty)
VALUES
  (1, 'Dr. Aris Thorne', 'Culinary Diagnostics'),
  (2, 'Dr. Beatrice Plum', 'Chronological Confusion'),
  (3, 'Dr. Caspian Vane', 'Gravity Resistance'),
  (4, 'Dr. Delilah Frost', 'Acute Dessert Therapy'),
  (5, 'Dr. Eldon Rook', 'Somnambulant Gymnastics'),
  (6, 'Dr. Fiona Gale', 'Over-enthusiastic Sneezing'),
  (7, 'Dr. Gideon Vance', 'Extreme Couch Potato-ism'),
  (8, 'Dr. Hazel Quinn', 'Spontaneous Melody Outbreaks'),
  (9, 'Dr. Ignatius Flint', 'Stubbed Toe Trauma'),
  (10, 'Dr. Juniper Slate', 'Advanced Broccoli Administration');

INSERT INTO Patients (PatientId, FirstName, LastName, DateOfBirth)
VALUES
  (1, 'Barnaby', 'Quigley', DATE '1982-04-12'),
  (2, 'Seraphina', 'Pockets', DATE '1995-11-23'),
  (3, 'Thaddeus', 'Plumbob', DATE '1978-01-30'),
  (4, 'Marigold', 'Swoon', DATE '2002-08-14'),
  (5, 'Silas', 'Fiddlewood', DATE '1965-06-05'),
  (6, 'Clementine', 'Fizz', DATE '1988-12-10'),
  (7, 'Orville', 'Snipe', DATE '1971-03-22'),
  (8, 'Rosalind', 'Furlong', DATE '1999-07-07'),
  (9, 'Percival', 'Gout', DATE '1955-09-18'),
  (10, 'Elara', 'Moonbeam', DATE '2010-02-28');

INSERT INTO Appointments (PatientId, AppointmentId, ProviderId, AppointmentDate, Status, DoctorNotes)
VALUES
  (1, 101, 1, DATE '2026-10-01', 'Completed', 'Patient complains of tasting the color blue. Prescribed 14 hours of video games and a large pizza.'),
  (2, 102, 3, DATE '2026-10-02', 'Completed', 'Patient accidentally swallowed a cloud. Floating slightly above the exam table. Needs a heavy lunch to weigh her down.'),
  (3, 103, 2, DATE '2026-10-03', 'Completed', 'Patient has developed a severe allergy to Mondays. Breaking out in hives when looking at a calendar.'),
  (4, 104, 6, DATE '2026-10-04', 'Completed', 'Patient reports excessive glitter in bloodstream after crafting accident. Sparkles violently when sneezing.'),
  (5, 105, 5, DATE '2026-10-05', 'Completed', 'Diagnosed with resting confused face. Patient forgot why he came to the clinic in the first place.'),
  (6, 106, 4, DATE '2026-10-06', 'Scheduled', 'Severe case of ice cream withdrawal. Symptoms include whining, shivering, and aggressively pointing at freezers.'),
  (7, 107, 7, DATE '2026-10-07', 'Completed', 'Patient left leg has fallen asleep and refuses to wake up without a bedtime story.'),
  (8, 108, 8, DATE '2026-10-08', 'Completed', 'Uncontrollable urge to tap dance when hearing elevator music. Ankles are showing signs of extreme wear.'),
  (9, 109, 9, DATE '2026-10-09', 'Scheduled', 'Patient believes his eyebrows are trying to escape. Taped them down pending further review.'),
  (10, 110, 10, DATE '2026-10-10', 'Completed', 'Diagnosed with acute vegetable aversion. Emits a high-pitched frequency when placed within 10 feet of broccoli.');

INSERT INTO Prescriptions (PatientId, PrescriptionId, ProviderId, MedicationName, DatePrescribed)
VALUES
  (1, 201, 1, 'Extra Cheese Pepperoni Pizza (Taken orally)', DATE '2026-10-01'),
  (2, 202, 3, 'Lead-weighted Boots (Wear daily)', DATE '2026-10-02'),
  (3, 203, 2, 'Time Machine set to Tuesday (Use once)', DATE '2026-10-03'),
  (4, 204, 6, 'Vacuum Cleaner on Reverse Mode (Apply to nose)', DATE '2026-10-04'),
  (5, 205, 5, 'A Map and a Compass (Consult twice daily)', DATE '2026-10-05'),
  (6, 206, 4, 'Three Scoops of Neapolitan Ice Cream (Stat!)', DATE '2026-10-06'),
  (7, 207, 7, 'Collection of Fairy Tales (Read to leg at 9PM)', DATE '2026-10-07'),
  (8, 208, 8, 'Noise Cancelling Headphones (Wear near lobbies)', DATE '2026-10-08'),
  (9, 209, 9, 'Heavy Duty Masking Tape (Apply to forehead)', DATE '2026-10-09'),
  (10, 210, 10, 'Chocolate-Covered Broccoli (To trick the system, eat with caution)', DATE '2026-10-10');

