-- SQL Server initialization script for CleaningFd database

USE master;
GO

-- Drop database if it exists (for clean start)
IF EXISTS (SELECT name FROM sys.databases WHERE name = 'CleaningFd')
BEGIN
    ALTER DATABASE [CleaningFd] SET SINGLE_USER WITH ROLLBACK IMMEDIATE;
    DROP DATABASE [CleaningFd];
END
GO

-- Create database
CREATE DATABASE [CleaningFd]
    COLLATE SQL_Latin1_General_CP1_CI_AS;
GO

USE [CleaningFd];
GO

-- The password should match DB_PASSWORD in your .env file
IF NOT EXISTS (SELECT * FROM sys.server_principals WHERE name = 'cleaningfd_app')
BEGIN
    CREATE LOGIN cleaningfd_app WITH PASSWORD = 'AppUser123!';
END
GO

IF NOT EXISTS (SELECT * FROM sys.database_principals WHERE name = 'cleaningfd_app')
BEGIN
    CREATE USER cleaningfd_app FOR LOGIN cleaningfd_app;
END
GO

-- Grant necessary permissions to application user
ALTER ROLE db_datareader ADD MEMBER cleaningfd_app; 
ALTER ROLE db_datawriter ADD MEMBER cleaningfd_app; 
ALTER ROLE db_ddladmin ADD MEMBER cleaningfd_app;
GO

-- Create table JobQueue
CREATE TABLE [dbo].[JobQueue] (
	[JobId] [uniqueidentifier] NOT NULL,
	[JobStatus] [varchar](20) NOT NULL,
	[CreatedAt] [datetime2](7) NULL,
	[StartedAt] [datetime2](7) NULL,
	[CompletedAt] [datetime2](7) NULL,
	[ErrorMessage] [nvarchar](max) NULL,
	[Payload] [nvarchar](max) NULL,
	[ServiceType] [varchar](255) NULL,
	[ResultData] [nvarchar](max) NULL,
	[ArmstrongData] [nvarchar](max) NULL,
);
GO

PRINT 'CleaningFd database initialized successfully!';
PRINT 'Application user "cleaningfd_app" created with appropriate permissions.';
